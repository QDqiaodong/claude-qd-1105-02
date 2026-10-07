package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Sale;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByStatus(String status);

    /**
     * 行级加锁读取销售单：结账 / 退款互斥，也与并发重复退款互斥。
     * 两个请求同时退同一张单时，先拿锁的完成状态流转，后拿锁的在锁内读到「已退款」，
     * 按业务规则拒绝（不会再加一次库存、不会写第二条流水），从根上保证退款只生效一次。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);
}
