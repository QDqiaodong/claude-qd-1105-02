package com.book.store.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sale_item")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "sale_id")
    public Long saleId;

    @Column(name = "book_id")
    public Long bookId;

    @Column(name = "qty", nullable = false)
    public Integer qty;

    // ---------------- 结账时固化的图书快照 ----------------
    // 结账那一刻把书名 / ISBN / 单价写进明细行本身：此后图书档案被改名、删除都不影响
    // 原销售单的展示、总额核对与退款提示。草稿阶段为空，结账时一次性补齐。

    @Column(name = "book_title", length = 128)
    public String bookTitle;

    @Column(name = "book_isbn", length = 32)
    public String bookIsbn;

    @Column(name = "book_price", precision = 10, scale = 2)
    public BigDecimal bookPrice;
}
