package com.book.store.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    public String name;

    @Column(name = "contact", length = 32)
    public String contact;

    @Column(name = "phone", length = 20)
    public String phone;

    /** 启用 / 停用（可追踪）。新供应商默认启用，旧记录由启动迁移回填为启用。 */
    @Column(name = "status", nullable = false, length = 16)
    public String status = "启用";

    /** 停用原因；启用时为空。 */
    @Column(name = "disabled_reason", length = 255)
    public String disabledReason;

    /** 最近一次停用 / 重新启用的时间。 */
    @Column(name = "status_changed_at")
    public LocalDateTime statusChangedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
