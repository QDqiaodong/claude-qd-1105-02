package com.book.store.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.book.store.common.ApiResponse;
import com.book.store.entity.Purchase;
import com.book.store.service.PurchaseService;
import com.book.store.service.PurchaseService.PurchaseCreate;
import com.book.store.service.PurchaseService.PurchaseDetail;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    public ApiResponse<java.util.List<Purchase>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(purchaseService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseDetail> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseService.detail(id));
    }

    @PostMapping
    public ApiResponse<Purchase> create(@RequestBody PurchaseCreate input) {
        return ApiResponse.success(purchaseService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Purchase> update(@PathVariable Long id, @RequestBody PurchaseCreate input) {
        return ApiResponse.success(purchaseService.update(id, input));
    }

    @PostMapping("/{id}/stock-in")
    public ApiResponse<Purchase> stockIn(@PathVariable Long id) {
        return ApiResponse.success(purchaseService.stockIn(id));
    }

    @PostMapping("/{id}/void")
    public ApiResponse<Purchase> voidPurchase(@PathVariable Long id) {
        return ApiResponse.success(purchaseService.voidPurchase(id));
    }
}
