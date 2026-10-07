package com.book.store.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.book.store.common.BizException;
import com.book.store.common.Dict;
import com.book.store.common.PageResult;
import com.book.store.entity.Book;
import com.book.store.repository.BookRepository;
import com.book.store.repository.BookSpecifications;

@Service
public class BookService {

    /** 分页上限：防止超大 size 拖垮查询 */
    private static final int MAX_PAGE_SIZE = 200;

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * 不分页列表（供导航计数、下拉选择等场景）。与分页查询共用同一套过滤规则。
     * 统一按 id 排序：任何筛选/分页组合下结果顺序稳定，不会因排序漂移跳错书。
     */
    public List<Book> list(String status, String keyword, String isbn) {
        return bookRepository.findAll(BookSpecifications.of(status, keyword, isbn), Sort.by("id"));
    }

    /**
     * 分页查询：内容与总数走同一个 Specification，过滤规则与不分页列表完全一致。
     */
    public PageResult<Book> page(String status, String keyword, String isbn, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, MAX_PAGE_SIZE);
        Page<Book> result = bookRepository.findAll(
                BookSpecifications.of(status, keyword, isbn),
                PageRequest.of(p - 1, s, Sort.by("id")));
        return new PageResult<>(result.getContent(), result.getTotalElements(), p, s);
    }

    /**
     * 详情：只按主键定位。从列表 / 销售单明细跳转时都带 id，
     * 与列表排序、分页无关，必定落到同一本书。
     */
    public Book get(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BizException("图书不存在"));
    }

    @Transactional
    public Book create(Book input) {
        if (input.isbn == null || input.isbn.isBlank()) {
            throw new BizException("ISBN 不能为空");
        }
        String isbn = input.isbn.trim();
        if (!bookRepository.findAllByIsbn(isbn).isEmpty()) {
            throw new BizException("ISBN 已存在，不可重复");
        }
        if (input.title == null || input.title.isBlank()) {
            throw new BizException("书名不能为空");
        }
        if (input.price == null || input.price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("定价必须大于 0");
        }
        if (input.stock == null || input.stock < 0) {
            throw new BizException("库存不能为负数");
        }
        Book book = new Book();
        book.isbn = isbn;
        book.title = input.title;
        book.author = input.author;
        book.price = input.price;
        book.stock = input.stock;
        // 库存为 0 默认置为缺货
        book.status = (input.stock == 0) ? "缺货" : "在售";
        return bookRepository.save(book);
    }

    @Transactional
    public Book update(Long id, Book input) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BizException("图书不存在"));
        if (input.isbn != null) {
            String isbn = input.isbn.trim();
            if (isbn.isEmpty()) {
                // 允许清空为「未登记」（兼容无 ISBN 的旧数据）；置空后不参与 ISBN 查询
                book.isbn = null;
            } else {
                boolean duplicated = bookRepository.findAllByIsbn(isbn).stream()
                        .anyMatch(b -> !b.id.equals(id));
                if (duplicated) {
                    throw new BizException("ISBN 已存在，不可重复");
                }
                book.isbn = isbn;
            }
        }
        if (input.title != null) {
            if (input.title.isBlank()) {
                throw new BizException("书名不能为空");
            }
            book.title = input.title;
        }
        if (input.author != null) {
            book.author = input.author;
        }
        if (input.price != null) {
            if (input.price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("定价必须大于 0");
            }
            book.price = input.price;
        }
        if (input.stock != null) {
            if (input.stock < 0) {
                throw new BizException("库存不能为负数");
            }
            book.stock = input.stock;
        }
        if (input.status != null) {
            if (!Dict.isValidBookStatus(input.status)) {
                throw new BizException("图书状态非法（应为在售/缺货/下架）");
            }
            book.status = input.status;
        }
        // 库存归零且原状态为在售，自动置缺货
        if (book.stock == 0 && "在售".equals(book.status)) {
            book.status = "缺货";
        }
        return bookRepository.save(book);
    }
}
