package com.book.store.common;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 书店业务词典与规则参数表：图书状态、进货单状态流转、销售单状态流转等。
 *
 * <p>规则集中在此，Service 里真正引用这些常量与校验方法做业务判断。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 图书 ----------------
    /** 在售 / 缺货 / 下架 */
    public static final List<String> BOOK_STATUS = List.of("在售", "缺货", "下架");

    // ---------------- 供应商 ----------------
    /** 启用 / 停用：停用后不得新建挂接它的采购草稿，已挂接的草稿保留关系但不能入库 */
    public static final String SUPPLIER_ENABLED = "启用";
    public static final String SUPPLIER_DISABLED = "停用";
    public static final List<String> SUPPLIER_STATUS = List.of(SUPPLIER_ENABLED, SUPPLIER_DISABLED);

    // ---------------- 进货单 ----------------
    /** 草稿 → 已入库 / 已作废；已入库 → 已作废（回滚库存） */
    public static final List<String> PURCHASE_STATUS = List.of("草稿", "已入库", "已作废");

    public static final List<String> PURCHASE_FLOW = List.of("草稿", "已入库", "已作废");

    // ---------------- 销售单 ----------------
    /** 草稿 → 已结账 → 已退款 */
    public static final List<String> SALE_STATUS = List.of("草稿", "已结账", "已退款");

    public static final List<String> SALE_FLOW = List.of("草稿", "已结账", "已退款");

    // ---------------- 通用规则 ----------------
    /** 手机号格式：1[3-9] 开头的 11 位号码 */
    public static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    // ---------------- 取值方法 ----------------

    public static boolean isValidBookStatus(String status) {
        return status != null && BOOK_STATUS.contains(status);
    }

    public static boolean isValidSupplierStatus(String status) {
        return status != null && SUPPLIER_STATUS.contains(status);
    }

    public static boolean isSupplierEnabled(String status) {
        return SUPPLIER_ENABLED.equals(status);
    }

    public static boolean isValidPurchaseStatus(String status) {
        return status != null && PURCHASE_STATUS.contains(status);
    }

    /** 进货单状态流转：草稿→已入库、草稿→已作废、已入库→已作废（回滚） */
    public static boolean canTransferPurchase(String from, String to) {
        if ("草稿".equals(from)) {
            return "已入库".equals(to) || "已作废".equals(to);
        }
        if ("已入库".equals(from)) {
            return "已作废".equals(to);
        }
        return false; // 已作废不可再流转
    }

    public static boolean isValidSaleStatus(String status) {
        return status != null && SALE_STATUS.contains(status);
    }

    /** 销售单状态流转：草稿→已结账、已结账→已退款 */
    public static boolean canTransferSale(String from, String to) {
        if ("草稿".equals(from)) {
            return "已结账".equals(to);
        }
        if ("已结账".equals(from)) {
            return "已退款".equals(to);
        }
        return false;
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && !phone.isBlank() && PHONE_PATTERN.matcher(phone).matches();
    }
}
