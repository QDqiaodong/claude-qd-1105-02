package com.book.store.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 供应商状态变更流水：每次停用 / 重新启用都插入一条，保证状态可追踪、可追溯。
 */
@Entity
@Table(name = "supplier_status_log")
public class SupplierStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "supplier_id", nullable = false)
    public Long supplierId;

    @Column(name = "from_status", length = 16)
    public String fromStatus;

    @Column(name = "to_status", nullable = false, length = 16)
    public String toStatus;

    @Column(name = "reason", length = 255)
    public String reason;

    @Column(name = "changed_at", nullable = false)
    public LocalDateTime changedAt;
}
