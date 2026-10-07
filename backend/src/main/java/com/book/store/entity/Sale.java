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
@Table(name = "sale")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "total", precision = 12, scale = 2)
    public BigDecimal total;

    @Column(name = "status", length = 16)
    public String status;

    /**
     * 退款时间：仅在状态变为「已退款」的同一事务内写入，财务与列表据此追溯退款发生时刻。
     * 旧库的已退款单由启动迁移回填（取建单时间兜底）。
     */
    @Column(name = "refunded_at")
    public LocalDateTime refundedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    // ---- 查询时拼装的提示（不落库）：如退款时部分明细的图书档案已缺失 ----

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String refundNote;
}
