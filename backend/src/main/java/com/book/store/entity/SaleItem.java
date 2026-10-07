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

    /**
     * 结账时固化的图书快照（书名 / ISBN / 单价）。销售单一结账即为历史凭证：
     * 之后图书改名、改价甚至档案被物理删除，本单展示与退款核对都以快照为准，
     * 不会因实时档案缺失而丢信息。草稿阶段这些列为空（结账才补）。
     */
    @Column(name = "book_title", length = 128)
    public String bookTitle;

    @Column(name = "book_isbn", length = 32)
    public String bookIsbn;

    @Column(name = "book_price", precision = 10, scale = 2)
    public BigDecimal bookPrice;
}
