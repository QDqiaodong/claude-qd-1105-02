package com.book.store.repository;

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

    java.util.List<Sale> findByStatus(String status);

    /**
     * 行级加锁读取销售单：结账 / 退款互斥。两名操作员（或重复点击、刷新重试）并发退款时，
     * 第二个请求在此等待，拿到锁后读到的已是「已退款」，直接被业务状态校验拒绝，
     * 库存只可能回滚一次，不会出现重复流水。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);
}
