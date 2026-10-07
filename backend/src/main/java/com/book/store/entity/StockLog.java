package com.book.store.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 库存流水：每一次真实的库存增减（销售出库 / 退款回滚 / 进货入库 / 作废回滚）
 * 都在同一事务内追加一行，库存与单据从此可以逐笔对账，不再分叉。
 *
 * <p>(biz_type, biz_id, book_id) 上有唯一索引：同一张单据对同一本书的同一类动作
 * 至多一行。并发重复退款 / 重复结账即使绕过了行锁校验，也会在写入流水时被唯一约束
 * 拦下并整体回滚，是「只产生一次库存回滚」的最后一道防线。</p>
 */
@Entity
@Table(name = "stock_log")
public class StockLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 图书 id */
    @Column(name = "book_id", nullable = false)
    public Long bookId;

    /** 变化数量：出库为负、入库/回滚为正 */
    @Column(name = "change_qty", nullable = false)
    public Integer changeQty;

    /** 本笔之后该图书的库存结存（写入时取锁内行的最新值） */
    @Column(name = "stock_after", nullable = false)
    public Integer stockAfter;

    /** 销售出库 / 退款回滚 / 进货入库 / 进货作废 */
    @Column(name = "biz_type", nullable = false, length = 16)
    public String bizType;

    /** 关联单据 id（销售单 / 进货单） */
    @Column(name = "biz_id", nullable = false)
    public Long bizId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
