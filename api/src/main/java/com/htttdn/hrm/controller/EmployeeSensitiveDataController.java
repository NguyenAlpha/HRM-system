package com.htttdn.hrm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.employee.UpdateEmployeeSensitiveRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.employee.EmployeeSensitiveResponse;
import com.htttdn.hrm.service.EmployeeSensitiveDataService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees/{employeeId}/sensitive")
public class EmployeeSensitiveDataController {

    private final EmployeeSensitiveDataService employeeSensitiveDataService;

    public EmployeeSensitiveDataController(EmployeeSensitiveDataService employeeSensitiveDataService) {
        this.employeeSensitiveDataService = employeeSensitiveDataService;
    }

    @GetMapping
    public ApiResult<EmployeeSensitiveResponse> get(@PathVariable Long employeeId) {
        return ApiResult.ok(employeeSensitiveDataService.get(employeeId));
    }

    @PutMapping
    public ApiResult<EmployeeSensitiveResponse> update(
        @PathVariable Long employeeId,
        @Valid @RequestBody UpdateEmployeeSensitiveRequest request
    ) {
        return ApiResult.ok(employeeSensitiveDataService.update(employeeId, request));
    }
}
