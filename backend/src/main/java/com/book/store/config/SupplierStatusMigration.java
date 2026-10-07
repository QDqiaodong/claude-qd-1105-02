package com.book.store.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 供应商「启用/停用」状态的兼容迁移：在应用启动后、对外提供服务前执行一次。
 *
 * <p>本项目 ddl-auto=none，全新环境由 mysql 镜像构建期导入的 schema.sql 建表；
 * 对于已经在运行的旧库（supplier 表还没有状态列），这里做幂等升级：</p>
 * <ol>
 *   <li>补齐 supplier.status / disabled_reason / status_changed_at 三列；</li>
 *   <li>已有供应商数据默认保持「启用」，status_changed_at 回填为 created_at；</li>
 *   <li>创建 supplier_status_log 状态变更流水表；</li>
 *   <li>为旧供应商补一条「建档默认启用」流水，保证历史可追溯。</li>
 * </ol>
 *
 * <p>所有判断都走 information_schema，重复执行无副作用（迁移过的库开销仅为几条查询）。</li>
 */
@Component
@Order(0)
public class SupplierStatusMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public SupplierStatusMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("supplier", "status",
                "VARCHAR(16) NOT NULL DEFAULT '启用' COMMENT '启用 / 停用'");
        addColumnIfMissing("supplier", "disabled_reason",
                "VARCHAR(255) NULL COMMENT '停用原因（启用时为空）'");
        addColumnIfMissing("supplier", "status_changed_at",
                "DATETIME NULL COMMENT '最近状态变更时间'");

        // 已有供应商默认保持启用：status 列为空或旧值时回填，状态变更时间取建档时间
        jdbc.update("UPDATE supplier SET status = '启用' WHERE status IS NULL OR status = '' OR status NOT IN ('启用','停用')");
        jdbc.update("UPDATE supplier SET disabled_reason = NULL WHERE status = '启用'");
        jdbc.update("UPDATE supplier SET status_changed_at = created_at WHERE status_changed_at IS NULL");

        if (!tableExists("supplier_status_log")) {
            jdbc.execute("""
                    CREATE TABLE supplier_status_log (
                        id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                        supplier_id BIGINT NOT NULL COMMENT '供应商 id',
                        from_status VARCHAR(16) COMMENT '变更前状态（初始回填为 NULL）',
                        to_status   VARCHAR(16) NOT NULL COMMENT '变更后状态：启用 / 停用',
                        reason      VARCHAR(255) COMMENT '停用原因 / 启用备注',
                        changed_at  DATETIME NOT NULL COMMENT '变更时间',
                        CONSTRAINT fk_ssl_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }

        // 旧供应商没有任何状态流水，补一条「建档默认启用」，保证每个供应商都有可追溯起点
        jdbc.update("""
                INSERT INTO supplier_status_log (supplier_id, from_status, to_status, reason, changed_at)
                SELECT s.id, NULL, '启用', '建档默认启用', COALESCE(s.status_changed_at, s.created_at, NOW())
                FROM supplier s
                LEFT JOIN supplier_status_log l ON l.supplier_id = s.id
                WHERE l.id IS NULL
                """);
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
