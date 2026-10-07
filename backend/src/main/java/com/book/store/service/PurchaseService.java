package com.book.store.service;

import java.math.BigDecimal;
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
import com.book.store.entity.Purchase;
import com.book.store.entity.PurchaseItem;
import com.book.store.entity.Supplier;
import com.book.store.repository.BookRepository;
import com.book.store.repository.PurchaseItemRepository;
import com.book.store.repository.PurchaseRepository;
import com.book.store.repository.SupplierRepository;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final BookRepository bookRepository;
    private final SupplierRepository supplierRepository;

    public PurchaseService(PurchaseRepository purchaseRepository,
                           PurchaseItemRepository purchaseItemRepository,
                           BookRepository bookRepository,
                           SupplierRepository supplierRepository) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.bookRepository = bookRepository;
        this.supplierRepository = supplierRepository;
    }

    // ---------------- DTO ----------------
    public static class ItemInput {
        public Long bookId;
        public Integer qty;
        public BigDecimal cost;
    }

    public static class PurchaseCreate {
        public Long supplierId;
        public List<ItemInput> items;
    }

    public static class PurchaseDetail {
        public Purchase purchase;
        public List<PurchaseItem> items;
        /** 供应商实时信息；供应商记录被物理删除时为 null，页面仍可查看原单 */
        public Supplier supplier;

        public PurchaseDetail(Purchase purchase, List<PurchaseItem> items, Supplier supplier) {
            this.purchase = purchase;
            this.items = items;
            this.supplier = supplier;
        }
    }

    // ---------------- 查询 ----------------
    public List<Purchase> list(String status) {
        List<Purchase> list = (status != null && !status.isBlank())
                ? purchaseRepository.findByStatus(status)
                : purchaseRepository.findAll();
        fillSupplierSnapshot(list);
        return list;
    }

    public PurchaseDetail detail(Long id) {
        Purchase purchase = purchaseRepository.findById(id).orElseThrow(() -> new BizException("进货单不存在"));
        fillSupplierSnapshot(List.of(purchase));
        Supplier supplier = purchase.supplierId == null ? null
                : supplierRepository.findById(purchase.supplierId).orElse(null);
        return new PurchaseDetail(purchase, purchaseItemRepository.findByPurchaseId(id), supplier);
    }

    /** 批量回填供应商名称 / 实时状态 / 停用原因，供列表与详情展示（只读，不加锁）。 */
    private void fillSupplierSnapshot(List<Purchase> list) {
        if (list.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (Purchase p : list) {
            if (p.supplierId != null) {
                ids.add(p.supplierId);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Supplier> map = new HashMap<>();
        for (Supplier s : supplierRepository.findAllById(ids)) {
            map.put(s.id, s);
        }
        for (Purchase p : list) {
            Supplier s = p.supplierId == null ? null : map.get(p.supplierId);
            if (s != null) {
                p.supplierName = s.name;
                p.supplierStatus = s.status;
                p.supplierDisabledReason = s.disabledReason;
            }
        }
    }

    // ---------------- 新建 / 编辑 ----------------

    /**
     * 新建采购草稿：只能挂接「启用」供应商。
     * 供应商行用 FOR UPDATE 加锁后再判状态，与停用动作互斥：
     * 要么本事务先拿锁（停用需等待，建单成功后停用再生效），要么停用先拿锁（这里读到停用，整单拒绝），
     * 不会出现「界面显示已停用却成功建出新单」。
     */
    @Transactional
    public Purchase create(PurchaseCreate input) {
        if (input.supplierId == null) {
            throw new BizException("请选择供应商");
        }
        Supplier supplier = supplierRepository.findByIdForUpdate(input.supplierId)
                .orElseThrow(() -> new BizException("供应商不存在"));
        if (!Dict.isSupplierEnabled(supplier.status)) {
            throw new BizException("供应商「" + supplier.name + "」已停用"
                    + reasonText(supplier.disabledReason) + "，不能新建采购草稿，请重新选择启用供应商");
        }
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("进货明细不能为空");
        }
        Purchase purchase = new Purchase();
        purchase.supplierId = input.supplierId;
        purchase.status = "草稿";
        purchase.total = BigDecimal.ZERO;
        Purchase saved = purchaseRepository.save(purchase);
        // persistItems 内逐项校验，任何一项不合法都抛异常 → 整个事务（含采购单头）回滚
        purchase.total = persistItems(saved.id, items);
        saved = purchaseRepository.save(saved);
        fillSupplierSnapshot(List.of(saved));
        return saved;
    }

    /**
     * 编辑草稿（补充 / 修改明细）。供应商停用后已存在的草稿保留原关系、仍可补充明细：
     * <ul>
     *   <li>未传 supplierId：维持原供应商，不做切换（停用供应商的草稿也能保存明细）；</li>
     *   <li>传入与原单相同的 supplierId：维持原关系；</li>
     *   <li>传入不同 supplierId：属于重新挂接，只允许改挂到「启用」供应商。</li>
     * </ul>
     */
    @Transactional
    public Purchase update(Long id, PurchaseCreate input) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("进货单不存在"));
        if (!"草稿".equals(purchase.status)) {
            throw new BizException("仅草稿状态可编辑");
        }
        if (input.supplierId != null && !input.supplierId.equals(purchase.supplierId)) {
            Supplier target = supplierRepository.findByIdForUpdate(input.supplierId)
                    .orElseThrow(() -> new BizException("供应商不存在"));
            if (!Dict.isSupplierEnabled(target.status)) {
                throw new BizException("供应商「" + target.name + "」已停用"
                        + reasonText(target.disabledReason) + "，不能把采购草稿改挂给它");
            }
            purchase.supplierId = input.supplierId;
        }
        // 未切换供应商时，不校验原供应商启停：停用不影响已有草稿保留关系、补充明细
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("进货明细不能为空");
        }
        purchaseItemRepository.deleteByPurchaseId(id);
        purchase.total = persistItems(id, items);
        Purchase saved = purchaseRepository.save(purchase);
        fillSupplierSnapshot(List.of(saved));
        return saved;
    }

    private BigDecimal persistItems(Long purchaseId, List<ItemInput> items) {
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (ItemInput it : items) {
            if (it.bookId == null) {
                throw new BizException("明细缺少图书");
            }
            if (bookRepository.findById(it.bookId).isEmpty()) {
                throw new BizException("明细中的图书不存在");
            }
            if (it.qty == null || it.qty <= 0) {
                throw new BizException("进货数量必须大于 0");
            }
            if (it.cost == null || it.cost.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("进价必须大于 0");
            }
            if (!seen.add(it.bookId)) {
                throw new BizException("同一图书在进货单内不可重复");
            }
            PurchaseItem pi = new PurchaseItem();
            pi.purchaseId = purchaseId;
            pi.bookId = it.bookId;
            pi.qty = it.qty;
            pi.cost = it.cost;
            purchaseItemRepository.save(pi);
            total = total.add(it.cost.multiply(BigDecimal.valueOf(it.qty)));
        }
        return total;
    }

    // ---------------- 状态流转 ----------------

    /**
     * 入库。前置条件全部在同一事务、同一批行锁内完成：
     * <ol>
     *   <li>FOR UPDATE 锁住采购单头 → 并发入库 / 作废互斥，杜绝重复入库、库存加两次；</li>
     *   <li>校验仍是草稿（并发已作废 / 已入库都会在此被拒）；</li>
     *   <li>FOR UPDATE 锁住供应商并重新校验启用状态：停用即整单拒绝，
     *       在改任何库存之前抛出异常，采购单状态、图书库存、明细全部回滚，不留半更新；</li>
     *   <li>逐本 FOR UPDATE 锁图书加库存，最后才把采购单置为已入库。</li>
     * </ol>
     * 与停用并发时由行锁决定先后：停用先提交 → 这里读到停用并拒绝；入库先提交 →
     * 停用随后生效（已入库单是历史事实，不被追溯影响）。重新启用后原草稿即可再次入库。
     */
    @Transactional
    public Purchase stockIn(Long id) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("进货单不存在"));
        if (!Dict.canTransferPurchase(purchase.status, "已入库")) {
            throw new BizException("仅草稿状态可入库（已作废不可再入库）");
        }

        // 入库前重新校验供应商状态（行锁内读最新值），停用即整单拒绝
        if (purchase.supplierId == null) {
            throw new BizException("进货单未挂接供应商，无法入库");
        }
        Supplier supplier = supplierRepository.findByIdForUpdate(purchase.supplierId)
                .orElseThrow(() -> new BizException("供应商记录已不存在，无法入库"));
        if (!Dict.isSupplierEnabled(supplier.status)) {
            throw new BizException("供应商「" + supplier.name + "」已停用"
                    + reasonText(supplier.disabledReason)
                    + "，本入库单被整单拒绝，库存未做任何变更；重新启用该供应商后可再次入库");
        }

        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseId(id);
        if (items.isEmpty()) {
            throw new BizException("进货单无明细，无法入库");
        }
        for (PurchaseItem it : items) {
            Book book = bookRepository.findByIdForUpdate(it.bookId)
                    .orElseThrow(() -> new BizException("明细图书已不存在"));
            book.stock += it.qty;
            // 入库后若有库存且原缺货，恢复在售
            if (book.stock > 0 && "缺货".equals(book.status)) {
                book.status = "在售";
            }
            bookRepository.save(book);
        }
        purchase.status = "已入库";
        Purchase saved = purchaseRepository.save(purchase);
        fillSupplierSnapshot(List.of(saved));
        return saved;
    }

    @Transactional
    public Purchase voidPurchase(Long id) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("进货单不存在"));
        if ("已作废".equals(purchase.status)) {
            throw new BizException("进货单已作废，不可重复操作");
        }
        if (!"草稿".equals(purchase.status) && !"已入库".equals(purchase.status)) {
            throw new BizException("当前状态不可作废");
        }
        // 已入库的作废需回滚库存
        if ("已入库".equals(purchase.status)) {
            for (PurchaseItem it : purchaseItemRepository.findByPurchaseId(id)) {
                Book book = bookRepository.findByIdForUpdate(it.bookId).orElse(null);
                if (book != null) {
                    book.stock = Math.max(0, book.stock - it.qty);
                    if (book.stock == 0 && "在售".equals(book.status)) {
                        book.status = "缺货";
                    }
                    bookRepository.save(book);
                }
            }
        }
        purchase.status = "已作废";
        Purchase saved = purchaseRepository.save(purchase);
        fillSupplierSnapshot(List.of(saved));
        return saved;
    }

    private static String reasonText(String reason) {
        return (reason != null && !reason.isBlank()) ? "（原因：" + reason.trim() + "）" : "";
    }
}
