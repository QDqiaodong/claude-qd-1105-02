package com.book.store.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.book.store.common.ApiResponse;
import com.book.store.entity.Supplier;
import com.book.store.entity.SupplierStatusLog;
import com.book.store.service.SupplierService;
import com.book.store.service.SupplierService.StatusInput;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ApiResponse<List<Supplier>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return ApiResponse.success(supplierService.list(keyword, status));
    }

    @GetMapping("/{id}/status-logs")
    public ApiResponse<List<SupplierStatusLog>> statusLogs(@PathVariable Long id) {
        return ApiResponse.success(supplierService.statusLogs(id));
    }

    @PostMapping
    public ApiResponse<Supplier> create(@RequestBody Supplier supplier) {
        return ApiResponse.success(supplierService.create(supplier));
    }

    @PutMapping("/{id}")
    public ApiResponse<Supplier> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        return ApiResponse.success(supplierService.update(id, supplier));
    }

    /** 停用 / 重新启用（独立动作，带停用原因并记录状态变更流水） */
    @PostMapping("/{id}/status")
    public ApiResponse<Supplier> changeStatus(@PathVariable Long id, @RequestBody StatusInput input) {
        return ApiResponse.success(supplierService.changeStatus(id, input));
    }
}
