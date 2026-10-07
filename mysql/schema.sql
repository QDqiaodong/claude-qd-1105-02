SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 书店 · 图书台账与进销存
-- 库名 book_store；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注意：mysql 容器是 UTC，时间戳一律写 UTC_TIMESTAMP() + INTERVAL 8 HOUR（北京时间）。

CREATE DATABASE IF NOT EXISTS book_store
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE book_store;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS sale_item;
DROP TABLE IF EXISTS purchase_item;
DROP TABLE IF EXISTS sale;
DROP TABLE IF EXISTS purchase;
DROP TABLE IF EXISTS supplier_status_log;
DROP TABLE IF EXISTS book;
DROP TABLE IF EXISTS supplier;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 图书 ----------------
-- isbn 可空：旧图书数据可能没有 ISBN（页面显示「未登记」，不参与 ISBN 查询）；
-- 唯一索引保留，MySQL 唯一索引允许多个 NULL。
CREATE TABLE book (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    isbn       VARCHAR(32) UNIQUE COMMENT 'ISBN（旧数据可空）',
    title      VARCHAR(128) NOT NULL COMMENT '书名',
    author     VARCHAR(64)  COMMENT '作者',
    price      DECIMAL(10,2) NOT NULL COMMENT '定价',
    stock      INT          NOT NULL COMMENT '库存',
    status     VARCHAR(16)  COMMENT '在售 / 缺货 / 下架',
    created_at DATETIME     COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 供应商 ----------------
-- status 启用/停用（可追踪）：disabled_reason 停用原因，status_changed_at 最近一次状态变更时间
-- 旧供应商记录在迁移脚本中默认回填为「启用」，status_changed_at 取 created_at。
CREATE TABLE supplier (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL UNIQUE COMMENT '供应商名',
    contact    VARCHAR(32) COMMENT '联系人',
    phone      VARCHAR(20) COMMENT '手机',
    status     VARCHAR(16) NOT NULL DEFAULT '启用' COMMENT '启用 / 停用',
    disabled_reason VARCHAR(255) COMMENT '停用原因（启用时为空）',
    status_changed_at DATETIME COMMENT '最近状态变更时间',
    created_at DATETIME    COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 供应商状态变更流水 ----------------
-- 每次停用 / 重新启用都留痕，保证状态可追踪、可追溯。
CREATE TABLE supplier_status_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT NOT NULL COMMENT '供应商 id',
    from_status VARCHAR(16) COMMENT '变更前状态（初始回填为 NULL）',
    to_status   VARCHAR(16) NOT NULL COMMENT '变更后状态：启用 / 停用',
    reason      VARCHAR(255) COMMENT '停用原因 / 启用备注',
    changed_at  DATETIME    NOT NULL COMMENT '变更时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 进货单 ----------------
CREATE TABLE purchase (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT COMMENT '供应商 id',
    total      DECIMAL(12,2) COMMENT '进货总额（自动汇总）',
    status     VARCHAR(16)  COMMENT '草稿 / 已入库 / 已作废',
    created_at DATETIME     COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT NOT NULL COMMENT '进货单 id',
    book_id    BIGINT NOT NULL COMMENT '图书 id',
    qty        INT NOT NULL COMMENT '进货数量',
    cost       DECIMAL(10,2) COMMENT '进价'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 销售单 ----------------
CREATE TABLE sale (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    total      DECIMAL(12,2) COMMENT '销售总额（自动汇总）',
    status     VARCHAR(16)  COMMENT '草稿 / 已结账 / 已退款',
    created_at DATETIME     COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sale_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id    BIGINT NOT NULL COMMENT '销售单 id',
    book_id    BIGINT NOT NULL COMMENT '图书 id',
    qty        INT NOT NULL COMMENT '销售数量'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 外键 ----------------
ALTER TABLE purchase ADD CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id);
ALTER TABLE purchase_item ADD CONSTRAINT fk_pi_purchase FOREIGN KEY (purchase_id) REFERENCES purchase (id);
ALTER TABLE purchase_item ADD CONSTRAINT fk_pi_book FOREIGN KEY (book_id) REFERENCES book (id);
ALTER TABLE sale_item ADD CONSTRAINT fk_si_sale FOREIGN KEY (sale_id) REFERENCES sale (id);
ALTER TABLE sale_item ADD CONSTRAINT fk_si_book FOREIGN KEY (book_id) REFERENCES book (id);
ALTER TABLE supplier_status_log ADD CONSTRAINT fk_ssl_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id);

-- =====================================================================
-- 种子数据（含缺货 / 下架 / 已作废 / 已退款 等异常态）
-- =====================================================================

INSERT INTO supplier (name, contact, phone, status, disabled_reason, status_changed_at, created_at) VALUES
('北京图书大厦', '王经理', '13800001111', '启用', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('上海世纪文景', '李女士', '13900002222', '启用', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('浙江新华书店', '陈主任', '13700003333', '启用', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY),
('广东教育出版社', '赵老师', '13600004444', '停用', '合同到期，暂停采购', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY);

-- 供应商状态流水：广东教育出版社经历 启用→停用（最近一次），历史留痕
INSERT INTO supplier_status_log (supplier_id, from_status, to_status, reason, changed_at) VALUES
(4, NULL,   '启用', '建档默认启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
(4, '启用', '停用', '合同到期，暂停采购', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY);

INSERT INTO book (isbn, title, author, price, stock, status, created_at) VALUES
('9787020000011', '红楼梦',     '曹雪芹',   59.00, 120, '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('9787020000028', '西游记',     '吴承恩',   49.50,   0, '缺货', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 58 DAY),
('9787020000035', '三国演义',   '罗贯中',   52.00,  80, '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 56 DAY),
('9787020000042', '水浒传',     '施耐庵',   48.00,  15, '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 54 DAY),
('9787020000059', '百年孤独',   '马尔克斯', 39.80,   0, '下架', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
('9787020000066', '三体',       '刘慈欣',   88.00, 200, '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 45 DAY),
('9787020000073', '活着',       '余华',     35.00,  60, '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY),
('9787020000080', '小王子',     '圣埃克苏佩里', 29.90, 5, '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 35 DAY),
-- 旧数据：无 ISBN 的老书（isbn 为 NULL），用于验证缺失值显示与「不参与 ISBN 查询」
(NULL, '老版连环画', '佚名', 12.00, 3, '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 90 DAY);

-- 进货单：p1、p2 已入库；p3 草稿（挂在已停用供应商 4 名下：关系保留、可补明细，但入库会被整单拒绝）；p4 已作废（异常态）
INSERT INTO purchase (supplier_id, total, status, created_at) VALUES
(1, 1180.00, '已入库', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 DAY),
(2,  995.00, '已入库', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 28 DAY),
(4,  300.00, '草稿',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY),
(1,  240.00, '已作废', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY);

INSERT INTO purchase_item (purchase_id, book_id, qty, cost) VALUES
(1, 1, 100, 10.00),  -- 红楼梦
(1, 6,   2, 90.00),  -- 三体
(2, 3,  50, 12.00),  -- 三国演义
(2, 7,  50,  7.90),  -- 活着
(3, 8,  20, 15.00),  -- 小王子（草稿）
(4, 4,   5, 48.00);  -- 水浒传（已作废）

-- 销售单：s1、s2 已结账；s3 已退款（异常态）
INSERT INTO sale (total, status, created_at) VALUES
(117.90, '已结账', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 12 DAY),
( 59.00, '已结账', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 DAY),
( 48.00, '已退款', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 25 DAY);

INSERT INTO sale_item (sale_id, book_id, qty) VALUES
(1, 6, 1),  -- 三体
(1, 8, 1),  -- 小王子
(2, 1, 1),  -- 红楼梦
(3, 4, 1);  -- 水浒传（已退款，库存已回滚）
