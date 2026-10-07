package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Supplier;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Supplier findByName(String name);

    List<Supplier> findByNameContaining(String keyword);

    List<Supplier> findByStatus(String status);

    /**
     * 行级加锁读取供应商：建草稿 / 改草稿 / 入库 / 停用启用 在同一把行锁上串行，
     * 保证「读取状态 → 业务判断 → 提交」期间状态不会被并发改掉，
     * 杜绝「界面显示已停用却成功建新单 / 停用后仍成功入库」。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT s FROM Supplier s WHERE s.id = :id")
    Optional<Supplier> findByIdForUpdate(@Param("id") Long id);
}
