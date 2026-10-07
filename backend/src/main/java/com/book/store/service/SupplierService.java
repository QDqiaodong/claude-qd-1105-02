package com.book.store.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.book.store.common.BizException;
import com.book.store.common.Dict;
import com.book.store.entity.Supplier;
import com.book.store.entity.SupplierStatusLog;
import com.book.store.repository.SupplierRepository;
import com.book.store.repository.SupplierStatusLogRepository;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierStatusLogRepository statusLogRepository;

    public SupplierService(SupplierRepository supplierRepository,
                           SupplierStatusLogRepository statusLogRepository) {
        this.supplierRepository = supplierRepository;
        this.statusLogRepository = statusLogRepository;
    }

    // ---------------- DTO ----------------
    public static class StatusInput {
        /** 目标状态：启用 / 停用 */
        public String status;
        /** 停用原因（停用时必填）；重新启用时可填备注，可空 */
        public String reason;
    }

    public List<Supplier> list(String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasStatus && !Dict.isValidSupplierStatus(status)) {
            throw new BizException("供应商状态只能是：启用 / 停用");
        }
        if (hasStatus && hasKeyword) {
            return supplierRepository.findByNameContaining(keyword).stream()
                    .filter(s -> status.equals(s.status))
                    .toList();
        }
        if (hasStatus) {
            return supplierRepository.findByStatus(status);
        }
        if (hasKeyword) {
            return supplierRepository.findByNameContaining(keyword);
        }
        return supplierRepository.findAll();
    }

    public List<SupplierStatusLog> statusLogs(Long id) {
        if (supplierRepository.findById(id).isEmpty()) {
            throw new BizException("供应商不存在");
        }
        return statusLogRepository.findBySupplierIdOrderByChangedAtDescIdDesc(id);
    }

    @Transactional
    public Supplier create(Supplier input) {
        if (input.name == null || input.name.isBlank()) {
            throw new BizException("供应商名称不能为空");
        }
        if (supplierRepository.findByName(input.name) != null) {
            throw new BizException("供应商名称已存在");
        }
        if (input.phone != null && !input.phone.isBlank() && !Dict.isValidPhone(input.phone)) {
            throw new BizException("手机号格式不正确（应为 1 开头的 11 位手机号）");
        }
        Supplier supplier = new Supplier();
        supplier.name = input.name;
        supplier.contact = input.contact;
        supplier.phone = input.phone;
        // 新建供应商一律默认启用，忽略客户端传入的状态 / 停用原因
        supplier.status = Dict.SUPPLIER_ENABLED;
        supplier.disabledReason = null;
        supplier.statusChangedAt = LocalDateTime.now();
        Supplier saved = supplierRepository.save(supplier);
        writeLog(saved.id, null, Dict.SUPPLIER_ENABLED, "建档默认启用", supplier.statusChangedAt);
        return saved;
    }

    /**
     * 普通资料编辑：只改名称 / 联系人 / 手机，不处理启用停用。
     * 状态必须走 {@link #changeStatus}，避免一个接口把资料和状态混在一起、绕过停用原因校验。
     */
    @Transactional
    public Supplier update(Long id, Supplier input) {
        Supplier supplier = supplierRepository.findById(id).orElseThrow(() -> new BizException("供应商不存在"));
        if (input.name != null) {
            if (input.name.isBlank()) {
                throw new BizException("供应商名称不能为空");
            }
            Supplier other = supplierRepository.findByName(input.name);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("供应商名称已存在");
            }
            supplier.name = input.name;
        }
        if (input.contact != null) {
            supplier.contact = input.contact;
        }
        if (input.phone != null) {
            if (!input.phone.isBlank() && !Dict.isValidPhone(input.phone)) {
                throw new BizException("手机号格式不正确（应为 1 开头的 11 位手机号）");
            }
            supplier.phone = input.phone;
        }
        return supplierRepository.save(supplier);
    }

    /**
     * 停用 / 重新启用。加行锁，与采购建档、入库在同一把供应商行锁上串行：
     * 状态判断通过后到事务提交前，其他事务无法改掉该供应商状态，也无法读到中间态。
     */
    @Transactional
    public Supplier changeStatus(Long id, StatusInput input) {
        if (input == null || !Dict.isValidSupplierStatus(input.status)) {
            throw new BizException("请提交有效的目标状态：启用 / 停用");
        }
        String target = input.status.trim();
        String reason = input.reason == null ? null : input.reason.trim();

        Supplier supplier = supplierRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("供应商不存在"));

        if (target.equals(supplier.status)) {
            throw new BizException(target.equals(Dict.SUPPLIER_DISABLED)
                    ? "供应商已是停用状态，无需重复停用"
                    : "供应商已是启用状态，无需重复启用");
        }
        if (Dict.SUPPLIER_DISABLED.equals(target) && (reason == null || reason.isEmpty())) {
            throw new BizException("停用供应商必须填写停用原因");
        }

        String from = supplier.status;
        LocalDateTime now = LocalDateTime.now();
        supplier.status = target;
        supplier.disabledReason = Dict.SUPPLIER_DISABLED.equals(target) ? reason : null;
        supplier.statusChangedAt = now;
        Supplier saved = supplierRepository.save(supplier);
        writeLog(saved.id, from, target, reason, now);
        return saved;
    }

    private void writeLog(Long supplierId, String from, String to, String reason, LocalDateTime at) {
        SupplierStatusLog log = new SupplierStatusLog();
        log.supplierId = supplierId;
        log.fromStatus = from;
        log.toStatus = to;
        log.reason = reason;
        log.changedAt = at;
        statusLogRepository.save(log);
    }
}
