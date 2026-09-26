package com.htttdn.hrm.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
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

    @GetMapping
    public ApiResult<Page<EmployeeSummaryResponse>> list(
        @PageableDefault(sort = "id", size = 20) Pageable pageable
    ) {
        return ApiResult.ok(employeeService.list(pageable));
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
}
