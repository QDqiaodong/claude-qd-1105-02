package com.book.store.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "purchase")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "supplier_id")
    public Long supplierId;

    @Column(name = "total", precision = 12, scale = 2)
    public BigDecimal total;

    @Column(name = "status", length = 16)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    // ---- 以下为查询时拼装的供应商快照（不落库），让列表/详情能直接显示供应商名称与实时状态 ----

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String supplierName;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String supplierStatus;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String supplierDisabledReason;
}
