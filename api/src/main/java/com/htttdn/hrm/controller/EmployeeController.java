package com.htttdn.hrm.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.employee.CompleteResignationRequest;
import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.dto.response.employee.EmployeeCreationResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;
import com.htttdn.hrm.service.EmployeeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<EmployeeCreationResponse> create(
        @Valid @RequestBody CreateEmployeeRequest request
    ) {
        return ApiResult.ok(employeeService.create(request));
    }

    @GetMapping
    public ApiResult<PagedResult<EmployeeSummaryResponse>> list(
        @PageableDefault(sort = "id", size = 20) Pageable pageable
    ) {
        return ApiResult.ok(PagedResult.of(employeeService.list(pageable)));
    }

    @GetMapping("/{employeeId}")
    public ApiResult<EmployeeDetailResponse> getById(@PathVariable Long employeeId) {
        return ApiResult.ok(employeeService.getById(employeeId));
    }

    @PutMapping("/{employeeId}")
    public ApiResult<EmployeeDetailResponse> update(
        @PathVariable Long employeeId,
        @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        return ApiResult.ok(employeeService.update(employeeId, request));
    }

    @PostMapping("/{employeeId}/confirm")
    public ApiResult<EmployeeDetailResponse> confirmEmployment(@PathVariable Long employeeId) {
        return ApiResult.ok(employeeService.confirmEmployment(employeeId));
    }

    @PostMapping("/{employeeId}/resignation")
    public ApiResult<EmployeeDetailResponse> completeResignation(
        @PathVariable Long employeeId,
        @Valid @RequestBody CompleteResignationRequest request
    ) {
        return ApiResult.ok(employeeService.completeResignation(
            employeeId, request.terminationDate(), request.terminationReason()
        ));
    }

    @PostMapping("/{employeeId}/soft-delete")
    public ApiResult<Void> softDelete(
        @PathVariable Long employeeId,
        @Valid @RequestBody SoftDeleteEmployeeRequest request
    ) {
        employeeService.softDelete(employeeId, request);
        return ApiResult.ok();
    }
}
