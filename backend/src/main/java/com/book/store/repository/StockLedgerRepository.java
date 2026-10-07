package com.book.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.book.store.entity.StockLedger;

public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

    /** 某本书的库存流水：时间正序、同时间按 id 正序，便于逐笔核对余量。 */
    List<StockLedger> findByBookIdOrderByCreatedAtAscIdAsc(Long bookId);
}
