package com.book.store.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 图书 ISBN 的兼容迁移：在应用启动后、对外提供服务前执行一次。
 *
 * <p>本项目 ddl-auto=none，全新环境由 mysql 镜像构建期导入的 schema.sql 建表
 * （isbn 可空 + 唯一索引）。对于已经在运行的旧库，这里做幂等升级：</p>
 * <ol>
 *   <li>旧 book 表没有 isbn 列 → 补列（可空）。老数据保持 NULL，
 *       页面显示「未登记」，不参与 ISBN 查询；</li>
 *   <li>旧 isbn 列是 NOT NULL → 放宽为可空，允许旧书没有 ISBN；</li>
 *   <li>isbn 列没有任何索引 → 尝试补唯一索引；若旧数据已存在重复 ISBN，
 *       则跳过索引（不阻塞启动），查询页面对重复值有明确提示。</li>
 * </ol>
 *
 * <p>所有判断都走 information_schema，重复执行无副作用。</p>
 */
@Component
@Order(1)
public class BookIsbnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookIsbnMigration.class);

    private final JdbcTemplate jdbc;

    public BookIsbnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!columnExists("book", "isbn")) {
            // 旧表没有 isbn 列：补可空列，老数据为 NULL（页面显示「未登记」）
            jdbc.execute("ALTER TABLE book ADD COLUMN isbn VARCHAR(32) NULL COMMENT 'ISBN' AFTER id");
            log.info("[BookIsbnMigration] book.isbn 列不存在，已补列（可空）");
        } else if (!isColumnNullable("book", "isbn")) {
            // 旧列是 NOT NULL：放宽为可空，允许旧书没有 ISBN
            jdbc.execute("ALTER TABLE book MODIFY COLUMN isbn VARCHAR(32) NULL COMMENT 'ISBN'");
            log.info("[BookIsbnMigration] book.isbn 已放宽为可空");
        }

        if (!hasIndexOnColumn("book", "isbn")) {
            Integer dup = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM (
                        SELECT isbn FROM book WHERE isbn IS NOT NULL GROUP BY isbn HAVING COUNT(*) > 1
                    ) t
                    """, Integer.class);
            if (dup != null && dup > 0) {
                // 旧数据有重复 ISBN：不强行加唯一索引（会失败并阻塞启动），
                // 应用层新增/修改仍做重复校验，查询页面会明确提示重复值
                log.warn("[BookIsbnMigration] book.isbn 存在 {} 组重复值，跳过唯一索引；请在页面核对后清理", dup);
            } else {
                jdbc.execute("ALTER TABLE book ADD UNIQUE KEY uk_book_isbn (isbn)");
                log.info("[BookIsbnMigration] book.isbn 唯一索引已补齐");
            }
        }
    }

    private boolean columnExists(String table, String column) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        return cnt != null && cnt > 0;
    }

    private boolean isColumnNullable(String table, String column) {
        String nullable = jdbc.queryForObject("""
                SELECT IS_NULLABLE FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
        return "YES".equalsIgnoreCase(nullable);
    }

    private boolean hasIndexOnColumn(String table, String column) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        return cnt != null && cnt > 0;
    }
}
