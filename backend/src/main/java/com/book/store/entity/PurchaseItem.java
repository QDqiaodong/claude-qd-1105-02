package com.book.store.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_item")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "purchase_id")
    public Long purchaseId;

    @Column(name = "book_id")
    public Long bookId;

    @Column(name = "qty", nullable = false)
    public Integer qty;

    @Column(name = "cost", precision = 10, scale = 2)
    public BigDecimal cost;
}
