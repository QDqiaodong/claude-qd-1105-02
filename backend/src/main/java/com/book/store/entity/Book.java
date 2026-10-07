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
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * ISBN 可空：旧图书数据可能没有 ISBN（迁移脚本会把旧表的 isbn 列放宽为可空）。
     * 唯一索引仍保留，MySQL 唯一索引允许多个 NULL，互不影响；
     * 为 NULL 的图书不参与 ISBN 查询，页面显示「未登记」。
     */
    @Column(name = "isbn", unique = true, length = 32)
    public String isbn;

    @Column(name = "title", nullable = false, length = 128)
    public String title;

    @Column(name = "author", length = 64)
    public String author;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    public BigDecimal price;

    @Column(name = "stock", nullable = false)
    public Integer stock;

    @Column(name = "status", length = 16)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
