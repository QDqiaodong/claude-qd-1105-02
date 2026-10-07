package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Purchase;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByStatus(String status);

    /**
     * 行级加锁读取进货单：入库 / 作废互斥，杜绝两个并发请求同时把一张草稿入库
     * （重复入库、库存被加两次）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Purchase p WHERE p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") Long id);
}
