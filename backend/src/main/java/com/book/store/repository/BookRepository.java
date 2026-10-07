package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Book;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /**
     * ISBN 精确匹配。旧库可能因历史原因存在重复 ISBN（唯一索引是后补的），
     * 故返回列表：调用方判空即「无重复」，判多条即「存在重复值」，不会抛错。
     */
    List<Book> findAllByIsbn(String isbn);

    /**
     * 入库加库存时行级加锁读取图书，串行更新 stock，避免并发入库丢失库存增量。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
}
