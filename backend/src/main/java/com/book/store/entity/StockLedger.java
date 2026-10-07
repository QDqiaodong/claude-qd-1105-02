package com.book.store.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 库存流水：每一次引起库存数量变化的业务动作（销售结账 / 退款 / 进货入库 / 入库单作废）
 * 都在同一事务内追加一条，只增不改，供财务核对与图书详情追溯。
 *
 * <p>{@code (biz_type, biz_id, book_id)} 上有数据库唯一约束：同一张单据对同一本书
 * 的同一类动作最多一条。即使应用层出问题（重复点击、刷新重试、两名操作员同时退款），
 * 第二次插入也会被数据库拒绝，配合销售单行锁，保证一次退款最多产生一次库存回滚，
 * 不会出现重复流水。</p>
 */
@Entity
@Table(name = "stock_ledger")
public class StockLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "book_id", nullable = false)
    public Long bookId;

    /** 销售结账 / 销售退款 / 进货入库 / 进货作废 */
    @Column(name = "biz_type", nullable = false, length = 16)
    public String bizType;

    /** 业务单据 id（销售单 / 进货单 id） */
    @Column(name = "biz_id", nullable = false)
    public Long bizId;

    /** 库存增量：入库 / 退款为正，结账扣减 / 作废回滚为负 */
    @Column(name = "change_qty", nullable = false)
    public Integer changeQty;

    /** 变更后的库存余量，方便逐笔核对 */
    @Column(name = "stock_after", nullable = false)
    public Integer stockAfter;

    @Column(name = "remark", length = 255)
    public String remark;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
