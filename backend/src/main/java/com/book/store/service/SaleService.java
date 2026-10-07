package com.book.store.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final StockLedgerService stockLedgerService;

    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       BookRepository bookRepository,
                       StockLedgerService stockLedgerService) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.bookRepository = bookRepository;
        this.stockLedgerService = stockLedgerService;
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
     * 页面以结账快照展示原书信息并明确提示档案缺失，而不是让详情报错。
     */
    public static class SaleItemView {
        public Long id;
        public Long bookId;
        public Integer qty;
        /** 结账时固化的原书信息：图书档案缺失 / 改名改价后仍可展示与核对 */
        public String bookTitle;
        public String bookIsbn;
        public java.math.BigDecimal bookPrice;
        public Book book;

        public SaleItemView(SaleItem item, Book book) {
            this.id = item.id;
            this.bookId = item.bookId;
            this.qty = item.qty;
            this.bookTitle = item.bookTitle;
            this.bookIsbn = item.bookIsbn;
            this.bookPrice = item.bookPrice;
            this.book = book;
        }

        /** 该明细引用的图书档案是否已缺失（无法做库存回滚，退款须整单拒绝） */
        public boolean isBookMissing() {
            return book == null;
        }
    }

    public static class SaleDetail {
        public Sale sale;
        public List<SaleItemView> items;
        /** 是否存在图书档案缺失的明细：为 true 时退款整单拒绝，页面禁用退款并提示 */
        public boolean hasMissingBook;

        public SaleDetail(Sale sale, List<SaleItemView> items, boolean hasMissingBook) {
            this.sale = sale;
            this.items = items;
            this.hasMissingBook = hasMissingBook;
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
        boolean hasMissingBook = false;
        for (SaleItem it : items) {
            Book live = it.bookId == null ? null : books.get(it.bookId);
            if (it.bookId != null && live == null) {
                hasMissingBook = true;
            }
            views.add(new SaleItemView(it, live));
        }
        return new SaleDetail(sale, views, hasMissingBook);
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
        Sale sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("销售单不存在"));
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

    /**
     * 结账。整单在一个事务内完成，前置校验全部通过后才动任何数据：
     * <ol>
     *   <li>FOR UPDATE 锁销售单头：重复结账 / 结账与退款并发互斥；</li>
     *   <li>按 book id 升序 FOR UPDATE 锁全部明细图书（与退款、其他结账统一加锁顺序，避免死锁），
     *       锁内读最新库存并校验「在售 + 库存充足」。本事务与并发销售单在同一把图书行锁上串行：
     *       对方先提交则这里读到扣减后的真实库存，不足即整单拒绝，绝不出现负库存、丢失更新；</li>
     *   <li>逐本扣库存、固化结账快照（书名/ISBN/单价）、写库存流水；</li>
     *   <li>最后置「已结账」。任一步失败整体回滚，无半更新。</li>
     * </ol>
     */
    @Transactional
    public Sale checkout(Long id) {
        Sale sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("销售单不存在"));
        if (!Dict.canTransferSale(sale.status, "已结账")) {
            throw alreadyMoved(sale, "结账");
        }
        List<SaleItem> items = saleItemRepository.findBySaleId(id);
        if (items.isEmpty()) {
            throw new BizException("销售单无明细，无法结账");
        }

        // 统一按 book id 加锁：与退款 / 其他销售单结账的加锁顺序一致，杜绝死锁
        Map<Long, Book> locked = lockBooks(items);

        // 锁内做全部前置校验，任何一本不满足都在改库存之前抛出 → 整单回滚
        for (SaleItem it : items) {
            Book book = locked.get(it.bookId);
            if (book == null) {
                throw new BizException("明细图书（#" + it.bookId + "）档案已缺失，无法结账，请联系管理员核对后重试");
            }
            if (!"在售".equals(book.status)) {
                throw new BizException("图书《" + book.title + "》当前为「" + book.status
                        + "」，不可销售，本单未扣减任何库存，请调整后重试");
            }
            if (book.stock < it.qty) {
                throw new BizException("图书《" + book.title + "》库存不足（仅剩 " + book.stock
                        + "，本单需 " + it.qty + "），本单未扣减任何库存，请调整数量后重试");
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem it : items) {
            Book book = locked.get(it.bookId);
            int before = book.stock;
            book.stock = before - it.qty;
            BigDecimal price = book.price;
            total = total.add(price.multiply(BigDecimal.valueOf(it.qty)));
            // 扣到 0 自动置缺货
            if (book.stock == 0) {
                book.status = "缺货";
            }
            bookRepository.save(book);
            // 固化结账快照：后续改名 / 改价 / 删除档案都不影响本单凭证与退款核对
            it.bookTitle = book.title;
            it.bookIsbn = book.isbn;
            it.bookPrice = price;
            saleItemRepository.save(it);
            stockLedgerService.record(book, StockLedgerService.BIZ_SALE_CHECKOUT, id,
                    -it.qty, "销售单 #" + id + " 结账扣减（结前库存 " + before + "）");
        }
        sale.total = total;
        sale.status = "已结账";
        return saleRepository.save(sale);
    }

    /**
     * 退款。库存回滚、状态变更、退款时间、退款流水在同一事务内一次性完成：
     * <ol>
     *   <li>FOR UPDATE 锁销售单头并在锁内复查状态：重复点击、刷新重试、两名操作员同时退款时，
     *       只有一个请求能在「已结账」状态下继续；后到者读到「已退款」直接拒绝，
     *       库存不会被加第二次，流水唯一约束再加一道数据库防线；</li>
     *   <li>按 book id 升序 FOR UPDATE 锁全部明细图书，与新销售单结账的加锁顺序一致，
     *       两边在同一把行锁上串行。退款只是加库存，锁内任何时刻库存校验与状态变更都一致：
     *       锁等待超时（5s）返回 409，单据未做任何变更，刷新后可安全重试；</li>
     *   <li>先整单校验：任一线明细的图书档案已缺失，则一本都不回滚、状态不改，
     *       整单拒绝并按快照给出明确提示（管理员补建档案后可按提示重试），不存在部分回滚；</li>
     *   <li>逐本加库存、写退款流水；缺货回正为在售（人工「下架」的不自动改回）；</li>
     *   <li>置「已退款」并写退款时间，同生共死，杜绝「页面显示已退款但库存未恢复」。</li>
     * </ol>
     */
    @Transactional
    public Sale refund(Long id) {
        Sale sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("销售单不存在"));
        if (!Dict.canTransferSale(sale.status, "已退款")) {
            throw alreadyMoved(sale, "退款");
        }
        List<SaleItem> items = saleItemRepository.findBySaleId(id);
        if (items.isEmpty()) {
            throw new BizException("销售单无明细，无法退款");
        }

        // 与结账统一加锁顺序（book id 升序）：退款与新销售单并发占用同书时在行锁上串行，不死锁
        Map<Long, Book> locked = lockBooks(items);

        // 先整单校验，任何一本缺失都在改库存之前拒绝：整单不动，允许按提示重试
        List<String> missing = new ArrayList<>();
        for (SaleItem it : items) {
            if (!locked.containsKey(it.bookId)) {
                missing.add(displayName(it));
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException("以下明细的图书档案已缺失，库存无法核对回滚，本单未做任何变更："
                    + String.join("、", missing)
                    + "。请联系管理员补建图书档案后，刷新页面重试退款");
        }

        // 校验通过后一次性回滚全部明细
        for (SaleItem it : items) {
            Book book = locked.get(it.bookId);
            int before = book.stock;
            book.stock = before + it.qty;
            // 仅「缺货」（卖光导致）回正为在售；人工「下架」的不自动改回
            if ("缺货".equals(book.status)) {
                book.status = "在售";
            }
            bookRepository.save(book);
            stockLedgerService.record(book, StockLedgerService.BIZ_SALE_REFUND, id,
                    it.qty, "销售单 #" + id + " 退款回滚（回滚前库存 " + before + "）");
        }
        sale.status = "已退款";
        sale.refundedAt = LocalDateTime.now();
        return saleRepository.save(sale);
    }

    /**
     * 按明细涉及的图书加 X 锁，返回 bookId → Book（按 id 升序，调用方遍历顺序稳定）。
     * 物理上已不存在的图书不出现在结果中（由调用方做整单缺失校验）。
     */
    private Map<Long, Book> lockBooks(List<SaleItem> items) {
        List<Long> ids = items.stream()
                .map(it -> it.bookId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        Map<Long, Book> locked = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (Book b : bookRepository.findAllByIdForUpdate(ids)) {
                locked.put(b.id, b);
            }
        }
        return locked;
    }

    /** 明细展示名：优先结账快照，档案缺失且无快照（极旧数据）时退回 id 占位 */
    private static String displayName(SaleItem it) {
        if (it.bookTitle != null && !it.bookTitle.isBlank()) {
            return "《" + it.bookTitle.trim() + "》";
        }
        return "图书（#" + it.bookId + "）";
    }

    /** 并发/重复操作时的状态提示：告诉操作员单据当前的真实状态，引导刷新 */
    private static BizException alreadyMoved(Sale sale, String action) {
        return switch (sale.status) {
            case "草稿" -> new BizException("销售单尚是草稿，不能" + action);
            case "已结账" -> new BizException("销售单已结账，无需重复操作");
            case "已退款" -> new BizException(
                    "销售单已退款（退款时间 " + fmt(sale.refundedAt) + "），库存已回滚，不能重复退款；如页面未更新请刷新");
            default -> new BizException("销售单当前状态为「" + sale.status + "」，不能" + action + "，请刷新后重试");
        };
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "未知" : t.toString().replace('T', ' ').substring(0, 19);
    }
}
