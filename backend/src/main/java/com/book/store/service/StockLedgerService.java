package com.book.store.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.book.store.entity.Book;
import com.book.store.entity.StockLedger;
import com.book.store.repository.StockLedgerRepository;

/**
 * 库存流水写入 / 查询。写入必须发生在调用方的业务事务内（本类不加独立事务），
 * 与库存数量更新、单据状态变更同生共死：业务回滚则流水也回滚，绝不产生
 * 「库存变了没流水 / 有流水库存没变」的分叉。
 */
@Service
public class StockLedgerService {

    /** 业务类型常量：与页面展示、唯一约束 (biz_type, biz_id, book_id) 配套，勿随意改名 */
    public static final String BIZ_SALE_CHECKOUT = "销售结账";
    public static final String BIZ_SALE_REFUND = "销售退款";
    public static final String BIZ_PURCHASE_IN = "进货入库";
    public static final String BIZ_PURCHASE_VOID = "进货作废";

    private final StockLedgerRepository ledgerRepository;

    public StockLedgerService(StockLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * 追加一条库存流水。changeQty 为本次增量（正：入库/退款回滚；负：销售扣减/作废回滚），
     * stockAfter 取已更新后的 book.stock。
     */
    public void record(Book book, String bizType, Long bizId, int changeQty, String remark) {
        StockLedger ledger = new StockLedger();
        ledger.bookId = book.id;
        ledger.bizType = bizType;
        ledger.bizId = bizId;
        ledger.changeQty = changeQty;
        ledger.stockAfter = book.stock;
        ledger.remark = remark;
        ledger.createdAt = LocalDateTime.now();
        ledgerRepository.save(ledger);
    }

    public List<StockLedger> listByBook(Long bookId) {
        return ledgerRepository.findByBookIdOrderByCreatedAtAscIdAsc(bookId);
    }
}
