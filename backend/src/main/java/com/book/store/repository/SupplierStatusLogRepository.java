package com.book.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.book.store.entity.SupplierStatusLog;

public interface SupplierStatusLogRepository extends JpaRepository<SupplierStatusLog, Long> {

    List<SupplierStatusLog> findBySupplierIdOrderByChangedAtDescIdDesc(Long supplierId);
}
