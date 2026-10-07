package com.book.store.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
     * 退款时间：退款事务提交时写入，与状态置「已退款」、库存回滚、退款流水在同一事务内完成，
     * 要么一起成功要么一起回滚，不会出现「已退款但没有退款时间」。列表 / 详情据此展示，可追溯。
     */
    @Column(name = "refunded_at")
    public LocalDateTime refundedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
