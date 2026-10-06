package com.htttdn.hrm.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollPeriodRequest;
import com.htttdn.hrm.dto.request.payroll.PayrollActionRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.dto.response.payroll.PayslipResponse;
import com.htttdn.hrm.service.PayrollService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {
    private final PayrollService service;

    public PayrollController(PayrollService service) {
        this.service = service;
    }

    @PostMapping("/periods")
    public ApiResult<PayrollPeriodResponse> create(@Valid @RequestBody CreatePayrollPeriodRequest request) {
        return ApiResult.ok(service.createPeriod(request));
    }

    @GetMapping("/periods")
    public ApiResult<PagedResult<PayrollPeriodResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(service.listPeriods(pageable)));
    }

    @GetMapping("/periods/{id}")
    public ApiResult<PayrollPeriodResponse> get(@PathVariable Long id) {
        return ApiResult.ok(service.getPeriodById(id));
    }

    @PostMapping("/periods/{id}/calculate")
    public ApiResult<PayrollPeriodResponse> calculate(@PathVariable Long id,
        @RequestBody(required = false) PayrollActionRequest request) {
        return ApiResult.ok(service.calculate(id, request == null ? new PayrollActionRequest() : request));
    }

    @PostMapping("/periods/{id}/approve")
    public ApiResult<PayrollPeriodResponse> approve(@PathVariable Long id) {
        return ApiResult.ok(service.approve(id, new PayrollActionRequest()));
    }

    @PostMapping("/periods/{id}/mark-paid")
    public ApiResult<PayrollPeriodResponse> markPaid(@PathVariable Long id,
        @RequestBody(required = false) PayrollActionRequest request) {
        return ApiResult.ok(service.markPaid(id, request == null ? new PayrollActionRequest() : request));
    }

    @PostMapping("/periods/{id}/lock")
    public ApiResult<PayrollPeriodResponse> lock(@PathVariable Long id) {
        return ApiResult.ok(service.lock(id, new PayrollActionRequest()));
    }

    @PostMapping("/periods/{id}/cancel")
    public ApiResult<PayrollPeriodResponse> cancel(@PathVariable Long id) {
        return ApiResult.ok(service.cancel(id));
    }

    @GetMapping("/payslips/{id}")
    public ApiResult<PayslipResponse> payslip(@PathVariable Long id) {
        return ApiResult.ok(service.getPayslip(id));
    }

    @GetMapping("/employees/{employeeId}/payslips")
    public ApiResult<PagedResult<PayslipResponse>> employeePayslips(@PathVariable Long employeeId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(service.listPayslipsForEmployee(employeeId, pageable)));
    }
}
