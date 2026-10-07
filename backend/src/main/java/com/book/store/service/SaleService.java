package com.book.store.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.book.store.entity.StockLog;
import com.book.store.repository.BookRepository;
import com.book.store.repository.SaleItemRepository;
import com.book.store.repository.SaleRepository;
import com.book.store.repository.StockLogRepository;

@Service
public class SaleService {

    /** 库存流水业务类型 */
    private static final String LOG_CHECKOUT = "销售出库";
    private static final String LOG_REFUND = "退款回滚";

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final BookRepository bookRepository;
    private final StockLogRepository stockLogRepository;

    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       BookRepository bookRepository,
                       StockLogRepository stockLogRepository) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.bookRepository = bookRepository;
        this.stockLogRepository = stockLogRepository;
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
     * 销售明细视图：优先展示结账时固化的图书快照（书名/ISBN/单价），
     * 快照缺失时再回退到实时档案。bookMissing 表示引用的图书档案已不存在，
     * 此时仍能用快照还原原单，页面给出明确提示而不是报错或显示空白。
     */
    public static class SaleItemView {
        public Long id;
        public Long bookId;
        public Integer qty;
        public Book book;
        public String bookTitle;
        public String bookIsbn;
        public BigDecimal bookPrice;
        public boolean bookMissing;

        public SaleItemView(SaleItem item, Book book) {
            this.id = item.id;
            this.bookId = item.bookId;
            this.qty = item.qty;
            this.book = book;
            this.bookMissing = book == null;
            // 快照优先：结账后图书被改名 / 删除，原单仍按成交时的内容展示
            this.bookTitle = item.bookTitle != null ? item.bookTitle : (book != null ? book.title : null);
            this.bookIsbn = item.bookIsbn != null ? item.bookIsbn : (book != null ? book.isbn : null);
            this.bookPrice = item.bookPrice != null ? item.bookPrice : (book != null ? book.price : null);
        }
    }

    public static class SaleDetail {
        public Sale sale;
        public List<SaleItemView> items;
        /** true 表示存在图书档案缺失的明细（页面据此提示） */
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
            Book book = it.bookId == null ? null : books.get(it.bookId);
            if (book == null) {
                hasMissingBook = true;
            }
            views.add(new SaleItemView(it, book));
        }
        if (hasMissingBook) {
            sale.refundNote = buildMissingBookNote(sale.status, views);
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
     * 结账。整个动作在一个事务、一批行锁内完成，杜绝半更新：
     * <ol>
     *   <li>FOR UPDATE 锁住销售单头：与退款 / 并发结账互斥；</li>
     *   <li>按图书 id 升序逐本 FOR UPDATE 加锁——与其他销售单、进货入库按相同顺序抢锁，
     *       不会死锁；校验与扣减之间持有的是锁内最新库存，重复结账 / 并发销售无法超卖成负库存；</li>
     *   <li>先对全部锁内图书做状态 / 库存校验，任何一本不满足都在改动前抛异常，整单拒绝；</li>
     *   <li>再统一扣库存、固化书名/ISBN/单价快照、追加「销售出库」库存流水，最后置已结账。</li>
     * </ol>
     */
    @Transactional
    public Sale checkout(Long id) {
        Sale sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("销售单不存在"));
        if (!Dict.canTransferSale(sale.status, "已结账")) {
            throw new BizException("仅草稿状态可结账（当前状态：" + sale.status + "）");
        }
        List<SaleItem> items = lockedItems(id);
        if (items.isEmpty()) {
            throw new BizException("销售单无明细，无法结账");
        }

        // 第一遍：锁内校验全部明细，任一不合法都在改库存之前整单拒绝
        Map<Long, Book> lockedBooks = new HashMap<>();
        for (SaleItem it : items) {
            if (it.bookId == null) {
                throw new BizException("销售单存在缺少图书的明细，无法结账");
            }
            Book book = bookRepository.findByIdForUpdate(it.bookId)
                    .orElseThrow(() -> new BizException("明细图书（#" + it.bookId + "）档案已不存在，无法结账"));
            if (!"在售".equals(book.status)) {
                throw new BizException("图书《" + book.title + "》非在售，不可销售，本单整单未结账、库存未变更");
            }
            if (book.stock < it.qty) {
                throw new BizException("图书《" + book.title + "》库存不足（仅剩 " + book.stock
                        + "，需 " + it.qty + "），本单整单未结账、库存未变更，请补货或调整数量后重试");
            }
            lockedBooks.put(book.id, book);
        }

        // 第二遍：全部校验通过后才扣减 + 写快照 + 写流水（同一事务，要么全成要么全回滚）
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem it : items) {
            Book book = lockedBooks.get(it.bookId);
            int after = book.stock - it.qty;
            book.stock = after;
            if (after == 0) {
                // 扣到 0 自动置缺货
                book.status = "缺货";
            }
            bookRepository.save(book);

            // 固化成交时快照：日后改名 / 删档都不影响原单核对与退款
            it.bookTitle = book.title;
            it.bookIsbn = book.isbn;
            it.bookPrice = book.price;
            saleItemRepository.save(it);

            total = total.add(book.price.multiply(BigDecimal.valueOf(it.qty)));
            writeLog(book.id, -it.qty, after, LOG_CHECKOUT, id);
        }
        sale.total = total;
        sale.status = "已结账";
        return saleRepository.save(sale);
    }

    /**
     * 退款。整个动作在一个事务、一批行锁内完成，保证一次性恢复：
     * <ol>
     *   <li>FOR UPDATE 锁住销售单头：重复点击、刷新重试、两名操作员并发退款在此串行，
     *       后到者读到「已退款」直接拒绝，库存只回滚一次；</li>
     *   <li>按图书 id 升序逐本 FOR UPDATE 加锁（与结账 / 入库同序，避免死锁）：
     *       锁内读取的库存已包含「新销售单占用」的最新结果，回滚在其基础上增加，
     *       不存在校验与变更之间的窗口，更不可能出现负库存；</li>
     *   <li>所有现存图书统一加库存、恢复在售、追加「退款回滚」流水，
     *       最后同一事务内写状态与退款时间。任一环节失败全部回滚，不留
     *       「页面已退款但库存没恢复」的半更新；</li>
     *   <li>明细引用的图书档案已缺失时不阻断退款：按原快照展示并给出明确提示，
     *       该明细没有可回滚的库存行，其余明细照常整单完成。</li>
     * </ol>
     * 库存流水的 (业务类型, 单据, 图书) 唯一索引是最后一道防线：重复流水无法落库。
     */
    @Transactional
    public Sale refund(Long id) {
        Sale sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("销售单不存在"));
        if ("已退款".equals(sale.status)) {
            // 并发的第二个请求 / 用户刷新后重试会走到这里：单据与库存都未再变更，明确拒绝并回传退款时间
            throw new BizException("该销售单已退款（退款时间：" + formatTime(sale.refundedAt)
                    + "），请勿重复操作，库存仅回滚过一次");
        }
        if (!Dict.canTransferSale(sale.status, "已退款")) {
            throw new BizException("仅已结账状态可退款（当前状态：" + sale.status + "）");
        }

        List<SaleItem> items = lockedItems(id);
        if (items.isEmpty()) {
            throw new BizException("销售单无明细，无法退款");
        }

        List<String> missingTitles = new ArrayList<>();
        for (SaleItem it : items) {
            Book book = it.bookId == null ? null : bookRepository.findByIdForUpdate(it.bookId).orElse(null);
            if (book == null) {
                // 档案缺失：保留明细与原快照、不产生库存回滚，退款仍整单完成，页面明确提示
                missingTitles.add(displayTitle(it));
                continue;
            }
            int after = book.stock + it.qty;
            book.stock = after;
            // 因本次退款回正、或原为缺货的，恢复为在售；下架书不自动改状态
            if (after > 0 && !"下架".equals(book.status)) {
                book.status = "在售";
            }
            bookRepository.save(book);
            writeLog(book.id, it.qty, after, LOG_REFUND, id);
        }

        sale.status = "已退款";
        sale.refundedAt = LocalDateTime.now();
        Sale saved = saleRepository.save(sale);
        if (!missingTitles.isEmpty()) {
            saved.refundNote = "退款完成。以下明细的图书档案已缺失，库存无对应记录可回滚，已按原快照保留明细："
                    + String.join("、", missingTitles);
        }
        return saved;
    }

    /** 取明细并按图书 id 升序排列：所有涉及多本图书的加锁路径统一顺序，避免跨事务死锁。 */
    private List<SaleItem> lockedItems(Long saleId) {
        List<SaleItem> items = new ArrayList<>(saleItemRepository.findBySaleId(saleId));
        items.sort(Comparator.comparing(it -> it.bookId == null ? Long.MAX_VALUE : it.bookId));
        return items;
    }

    private void writeLog(Long bookId, int changeQty, int stockAfter, String bizType, Long bizId) {
        StockLog log = new StockLog();
        log.bookId = bookId;
        log.changeQty = changeQty;
        log.stockAfter = stockAfter;
        log.bizType = bizType;
        log.bizId = bizId;
        log.createdAt = LocalDateTime.now();
        stockLogRepository.save(log);
    }

    private static String displayTitle(SaleItem it) {
        if (it.bookTitle != null && !it.bookTitle.isBlank()) {
            return "《" + it.bookTitle + "》";
        }
        return "图书档案 #" + it.bookId;
    }

    private static String buildMissingBookNote(String status, List<SaleItemView> views) {
        List<String> titles = new ArrayList<>();
        for (SaleItemView v : views) {
            if (v.bookMissing) {
                if (v.bookTitle != null && !v.bookTitle.isBlank()) {
                    titles.add("《" + v.bookTitle + "》");
                } else {
                    titles.add("图书档案 #" + v.bookId);
                }
            }
        }
        if (titles.isEmpty()) {
            return null;
        }
        String tail = "已退款".equals(status)
                ? "退款时该部分库存无对应记录可回滚，其余明细已在同一事务整单恢复。"
                : "结账后若档案仍缺失，退款时该部分库存将无法回滚。";
        return "该单含图书档案缺失的明细（" + String.join("、", titles)
                + "），页面按结账时快照展示原内容；" + tail;
    }

    private static String formatTime(LocalDateTime t) {
        return t == null ? "未知" : t.toString().replace('T', ' ').substring(0, 16);
    }
}
