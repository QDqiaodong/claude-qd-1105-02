package com.book.store.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.book.store.common.BizException;
import com.book.store.common.Dict;
import com.book.store.entity.Book;
import com.book.store.entity.Sale;
import com.book.store.entity.SaleItem;
import com.book.store.repository.BookRepository;
import com.book.store.repository.SaleItemRepository;
import com.book.store.repository.SaleRepository;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final BookRepository bookRepository;

    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       BookRepository bookRepository) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.bookRepository = bookRepository;
    }

    // ---------------- DTO ----------------
    public static class ItemInput {
        public Long bookId;
        public Integer qty;
    }

    public static class SaleCreate {
        public List<ItemInput> items;
    }

    /**
     * 销售明细视图：明细字段之外带上图书实时信息（书名/ISBN/定价/状态），
     * 供页面展示与「查看图书」跳转。图书档案缺失（旧数据）时 book 为 null，
     * 页面显示为空而不是让详情报错。
     */
    public static class SaleItemView {
        public Long id;
        public Long bookId;
        public Integer qty;
        public Book book;

        public SaleItemView(SaleItem item, Book book) {
            this.id = item.id;
            this.bookId = item.bookId;
            this.qty = item.qty;
            this.book = book;
        }
    }

    public static class SaleDetail {
        public Sale sale;
        public List<SaleItemView> items;

        public SaleDetail(Sale sale, List<SaleItemView> items) {
            this.sale = sale;
            this.items = items;
        }
    }

    // ---------------- 查询 ----------------
    public List<Sale> list(String status) {
        if (status != null && !status.isBlank()) {
            return saleRepository.findByStatus(status);
        }
        return saleRepository.findAll();
    }

    public SaleDetail detail(Long id) {
        Sale sale = saleRepository.findById(id).orElseThrow(() -> new BizException("销售单不存在"));
        List<SaleItem> items = saleItemRepository.findBySaleId(id);
        // 批量取明细引用的图书；引用了已不存在图书的旧明细，book 置 null，不阻塞详情打开
        Set<Long> bookIds = new HashSet<>();
        for (SaleItem it : items) {
            if (it.bookId != null) {
                bookIds.add(it.bookId);
            }
        }
        Map<Long, Book> books = new HashMap<>();
        for (Book b : bookRepository.findAllById(bookIds)) {
            books.put(b.id, b);
        }
        List<SaleItemView> views = new ArrayList<>(items.size());
        for (SaleItem it : items) {
            views.add(new SaleItemView(it, it.bookId == null ? null : books.get(it.bookId)));
        }
        return new SaleDetail(sale, views);
    }

    // ---------------- 新建 / 编辑 ----------------
    @Transactional
    public Sale create(SaleCreate input) {
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("销售明细不能为空");
        }
        Sale sale = new Sale();
        sale.status = "草稿";
        sale.total = BigDecimal.ZERO;
        Sale saved = saleRepository.save(sale);
        persistItems(saved.id, items);
        return saved;
    }

    @Transactional
    public Sale update(Long id, SaleCreate input) {
        Sale sale = saleRepository.findById(id).orElseThrow(() -> new BizException("销售单不存在"));
        if (!"草稿".equals(sale.status)) {
            throw new BizException("仅草稿状态可编辑");
        }
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("销售明细不能为空");
        }
        saleItemRepository.deleteBySaleId(id);
        persistItems(id, items);
        return sale;
    }

    private void persistItems(Long saleId, List<ItemInput> items) {
        Set<Long> seen = new HashSet<>();
        for (ItemInput it : items) {
            if (it.bookId == null) {
                throw new BizException("明细缺少图书");
            }
            Book book = bookRepository.findById(it.bookId).orElseThrow(() -> new BizException("明细中的图书不存在"));
            if (!"在售".equals(book.status)) {
                throw new BizException("图书《" + book.title + "》非在售，不可销售");
            }
            if (it.qty == null || it.qty <= 0) {
                throw new BizException("销售数量必须大于 0");
            }
            if (seen.contains(it.bookId)) {
                throw new BizException("同一图书在销售单内不可重复");
            }
            seen.add(it.bookId);
            SaleItem si = new SaleItem();
            si.saleId = saleId;
            si.bookId = it.bookId;
            si.qty = it.qty;
            saleItemRepository.save(si);
        }
    }

    // ---------------- 状态流转 ----------------
    @Transactional
    public Sale checkout(Long id) {
        Sale sale = saleRepository.findById(id).orElseThrow(() -> new BizException("销售单不存在"));
        if (!Dict.canTransferSale(sale.status, "已结账")) {
            throw new BizException("仅草稿状态可结账");
        }
        List<SaleItem> items = saleItemRepository.findBySaleId(id);
        if (items.isEmpty()) {
            throw new BizException("销售单无明细，无法结账");
        }
        // 先校验：引用图书须为在售且库存充足，不足则整单不扣
        for (SaleItem it : items) {
            Book book = bookRepository.findById(it.bookId).orElseThrow(() -> new BizException("明细图书已不存在"));
            if (!"在售".equals(book.status)) {
                throw new BizException("图书《" + book.title + "》非在售，不可销售");
            }
            if (book.stock < it.qty) {
                throw new BizException("图书《" + book.title + "》库存不足（仅剩 " + book.stock + "）");
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem it : items) {
            Book book = bookRepository.findById(it.bookId).orElseThrow();
            book.stock -= it.qty;
            total = total.add(book.price.multiply(BigDecimal.valueOf(it.qty)));
            // 扣到 0 自动置缺货
            if (book.stock == 0) {
                book.status = "缺货";
            }
            bookRepository.save(book);
        }
        sale.total = total;
        sale.status = "已结账";
        return saleRepository.save(sale);
    }

    @Transactional
    public Sale refund(Long id) {
        Sale sale = saleRepository.findById(id).orElseThrow(() -> new BizException("销售单不存在"));
        if (!Dict.canTransferSale(sale.status, "已退款")) {
            throw new BizException("仅已结账状态可退款");
        }
        for (SaleItem it : saleItemRepository.findBySaleId(id)) {
            Book book = bookRepository.findById(it.bookId).orElse(null);
            if (book != null) {
                book.stock += it.qty;
                // 已缺货的回正为在售
                if ("缺货".equals(book.status)) {
                    book.status = "在售";
                }
                bookRepository.save(book);
            }
        }
        sale.status = "已退款";
        return saleRepository.save(sale);
    }
}
