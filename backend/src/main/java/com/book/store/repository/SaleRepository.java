package com.book.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.book.store.entity.Sale;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByStatus(String status);
}
