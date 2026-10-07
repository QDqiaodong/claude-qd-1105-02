package com.book.store.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 销售退款与库存流水的兼容迁移：应用启动后、对外提供服务前幂等执行一次。
 *
 * <p>全新环境由 mysql 镜像构建期导入的 schema.sql 直接建好全部结构；对已在运行的旧库：</p>
 * <ol>
 *   <li>sale 补 refunded_at（可追溯的退款时间），历史「已退款」单按建单时间回填；</li>
 *   <li>sale_item 补 book_title / book_isbn / book_price 快照列，
 *       并用现存图书档案回填历史结账明细，保证图书日后被改名 / 删档时原单仍可核对；</li>
 *   <li>新建 stock_log 库存流水表（含「业务类型 + 单据 + 图书」唯一索引），
 *       此后结账 / 退款 / 入库 / 作废的每一笔库存变动都留痕，并发重复操作无法写出重复流水。</li>
 * </ol>
 *
 * <p>所有判断都走 information_schema，重复执行无副作用。</p>
 */
@Component
@Order(2)
public class RefundAndStockLogMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public RefundAndStockLogMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1. sale.refunded_at
        addColumnIfMissing("sale", "refunded_at", "DATETIME NULL COMMENT '退款时间（仅已退款单有值）'");
        // 历史已退款单没有记录真实退款时刻，用建单时间兜底回填，保证每笔退款都有可追溯时间
        jdbc.update("UPDATE sale SET refunded_at = created_at WHERE status = '已退款' AND refunded_at IS NULL");

        // 2. sale_item 快照列
        addColumnIfMissing("sale_item", "book_title", "VARCHAR(128) NULL COMMENT '结账时书名快照'");
        addColumnIfMissing("sale_item", "book_isbn", "VARCHAR(32) NULL COMMENT '结账时 ISBN 快照'");
        addColumnIfMissing("sale_item", "book_price", "DECIMAL(10,2) NULL COMMENT '结账时单价快照'");
        // 用现存档案回填历史明细快照；档案已缺失的明细保持 NULL，页面按「档案缺失」提示
        jdbc.update("""
                UPDATE sale_item si
                JOIN book b ON b.id = si.book_id
                SET si.book_title = COALESCE(si.book_title, b.title),
                    si.book_isbn  = COALESCE(si.book_isbn, b.isbn),
                    si.book_price = COALESCE(si.book_price, b.price)
                WHERE si.book_title IS NULL OR si.book_isbn IS NULL OR si.book_price IS NULL
                """);

        // 3. stock_log 库存流水表（唯一索引兜底重复结账 / 重复退款）
        if (!tableExists("stock_log")) {
            jdbc.execute("""
                    CREATE TABLE stock_log (
                        id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                        book_id     BIGINT NOT NULL COMMENT '图书 id',
                        change_qty  INT NOT NULL COMMENT '变化数量：出库为负、入库/回滚为正',
                        stock_after INT NOT NULL COMMENT '本笔之后的库存结存',
                        biz_type    VARCHAR(16) NOT NULL COMMENT '销售出库 / 退款回滚 / 进货入库 / 进货作废',
                        biz_id      BIGINT NOT NULL COMMENT '关联单据 id',
                        created_at  DATETIME NOT NULL COMMENT '发生时间',
                        UNIQUE KEY uk_stock_log_biz (biz_type, biz_id, book_id),
                        KEY idx_stock_log_book (book_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private boolean tableExists(String table) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Integer.class, table);
        return cnt != null && cnt > 0;
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        if (cnt == null || cnt == 0) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
