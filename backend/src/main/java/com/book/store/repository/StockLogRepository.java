package com.book.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.book.store.entity.StockLog;

public interface StockLogRepository extends JpaRepository<StockLog, Long> {
}
