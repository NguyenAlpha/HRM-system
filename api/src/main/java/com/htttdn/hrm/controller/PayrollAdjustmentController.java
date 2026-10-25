package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollAdjustmentRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.payroll.PayrollAdjustmentResponse;
import com.htttdn.hrm.service.PayrollAdjustmentService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payroll/periods/{periodId}/adjustments")
@RequiredArgsConstructor
public class PayrollAdjustmentController {
    private final PayrollAdjustmentService service;

    @GetMapping
    public ApiResult<List<PayrollAdjustmentResponse>> list(@PathVariable Long periodId) {
        return ApiResult.ok(service.list(periodId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<PayrollAdjustmentResponse> create(@PathVariable Long periodId,
        @Valid @RequestBody CreatePayrollAdjustmentRequest request) {
        return ApiResult.ok(service.create(periodId, request));
    }

    @DeleteMapping("/{adjustmentId}")
    public ApiResult<Void> delete(@PathVariable Long periodId, @PathVariable Long adjustmentId) {
        service.delete(periodId, adjustmentId);
        return ApiResult.ok();
    }
}
