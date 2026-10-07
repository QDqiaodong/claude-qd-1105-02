package com.book.store.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 销售单退款能力的兼容迁移：应用启动后、对外提供服务前幂等执行一次。
 *
 * <p>本项目 ddl-auto=none，全新环境由 mysql 镜像构建期导入的 schema.sql 直接带新结构；
 * 对已在运行的旧库做以下升级（全部走 information_schema 判断，重复执行无副作用）：</p>
 * <ol>
 *   <li>sale 补 refunded_at（退款时间，与状态 / 库存回滚 / 流水同事务写入，可追溯）；
 *       旧的「已退款」单据没有退款时间，回填为 created_at（至少有一个可展示的历史时间）；</li>
 *   <li>sale_item 补 book_title / book_isbn / book_price 结账快照列；
 *       已结账 / 已退款的旧明细按当前图书档案回填快照，档案已缺失的保持 NULL，
 *       详情页会明确提示「图书档案缺失」而不是丢信息；</li>
 *   <li>建 stock_ledger 库存流水表 + (biz_type,biz_id,book_id) 唯一约束（防重复流水）+
 *       book.stock 非负 CHECK 约束（防负库存的数据库兜底）。</li>
 * </ol>
 *
 * <p>历史单据发生在本表建立之前，不伪造历史流水；从本次升级起，所有结账 / 退款 / 入库 / 作废
 * 都会在业务事务内追加流水，财务库存与单据记录从该时点起逐笔对得上。</p>
 */
@Component
@Order(2)
public class SaleRefundMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SaleRefundMigration.class);

    private final JdbcTemplate jdbc;

    public SaleRefundMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1) sale.refunded_at
        addColumnIfMissing("sale", "refunded_at", "DATETIME NULL COMMENT '退款时间（与状态/库存回滚同事务写入）'");
        int backfilled = jdbc.update(
                "UPDATE sale SET refunded_at = created_at WHERE status = '已退款' AND refunded_at IS NULL");
        if (backfilled > 0) {
            log.info("[SaleRefundMigration] 已为 {} 张历史已退款销售单回填退款时间", backfilled);
        }

        // 2) sale_item 结账快照列
        addColumnIfMissing("sale_item", "book_title", "VARCHAR(128) NULL COMMENT '结账时书名快照'");
        addColumnIfMissing("sale_item", "book_isbn", "VARCHAR(32) NULL COMMENT '结账时 ISBN 快照'");
        addColumnIfMissing("sale_item", "book_price", "DECIMAL(10,2) NULL COMMENT '结账时单价快照'");

        // 已结账 / 已退款的旧明细按当前档案回填快照；档案已删除的行不受影响（保持 NULL，页面提示缺失）
        int snap = jdbc.update("""
                UPDATE sale_item si
                JOIN sale s ON s.id = si.sale_id
                JOIN book b ON b.id = si.book_id
                SET si.book_title = b.title, si.book_isbn = b.isbn, si.book_price = b.price
                WHERE s.status IN ('已结账','已退款') AND si.book_title IS NULL
                """);
        if (snap > 0) {
            log.info("[SaleRefundMigration] 已为 {} 条历史销售明细回填结账快照", snap);
        }

        // 3) stock_ledger 库存流水表（含防重复流水唯一键）
        if (!tableExists("stock_ledger")) {
            jdbc.execute("""
                    CREATE TABLE stock_ledger (
                        id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                        book_id     BIGINT NOT NULL COMMENT '图书 id',
                        biz_type    VARCHAR(16) NOT NULL COMMENT '销售结账 / 销售退款 / 进货入库 / 进货作废',
                        biz_id      BIGINT NOT NULL COMMENT '业务单据 id',
                        change_qty  INT NOT NULL COMMENT '库存增量（正：入库/退款；负：扣减/作废回滚）',
                        stock_after INT NOT NULL COMMENT '变更后库存余量',
                        remark      VARCHAR(255) NULL COMMENT '备注',
                        created_at  DATETIME NOT NULL COMMENT '流水时间',
                        CONSTRAINT uk_stock_ledger_biz UNIQUE (biz_type, biz_id, book_id),
                        CONSTRAINT fk_sl_book FOREIGN KEY (book_id) REFERENCES book (id),
                        INDEX idx_book_time (book_id, created_at, id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.info("[SaleRefundMigration] stock_ledger 库存流水表已创建");
        }

        // 4) book.stock 非负约束：数据库层兜底，任何 bug / 并发都不能把库存写成负数。
        // 旧库若已存在历史负库存（旧版并发丢更新可能造成），加约束会失败：不阻塞启动，
        // 记录告警提示先盘点修正，修完后重启会自动补上（幂等）。
        if (!constraintExists("book", "chk_book_stock_nonneg")) {
            try {
                jdbc.execute("ALTER TABLE book ADD CONSTRAINT chk_book_stock_nonneg CHECK (stock >= 0)");
            } catch (Exception e) {
                log.warn("[SaleRefundMigration] book.stock 非负约束添加失败，可能存在历史负库存，"
                        + "请盘点修正后重启应用自动补约束：{}", e.getMessage());
            }
        }
    }

    private boolean tableExists(String table) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Integer.class, table);
        return cnt != null && cnt > 0;
    }

    private boolean constraintExists(String table, String constraint) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = ? AND constraint_name = ?
                """, Integer.class, table, constraint);
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
