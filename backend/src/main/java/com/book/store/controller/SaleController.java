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
import com.book.store.entity.Sale;
import com.book.store.service.SaleService;
import com.book.store.service.SaleService.SaleCreate;
import com.book.store.service.SaleService.SaleDetail;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    public ApiResponse<java.util.List<Sale>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(saleService.list(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SaleDetail> detail(@PathVariable Long id) {
        return ApiResponse.success(saleService.detail(id));
    }

    @PostMapping
    public ApiResponse<Sale> create(@RequestBody SaleCreate input) {
        return ApiResponse.success(saleService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Sale> update(@PathVariable Long id, @RequestBody SaleCreate input) {
        return ApiResponse.success(saleService.update(id, input));
    }

    @PostMapping("/{id}/checkout")
    public ApiResponse<Sale> checkout(@PathVariable Long id) {
        return ApiResponse.success(saleService.checkout(id));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<Sale> refund(@PathVariable Long id) {
        return ApiResponse.success(saleService.refund(id));
    }
}
