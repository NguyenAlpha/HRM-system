package com.htttdn.hrm.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.employee.EmployeeSalaryHistoryService;
import com.htttdn.hrm.service.employee.EmployeeSalaryHistoryService.SalaryHistoryView;
import com.htttdn.hrm.service.employee.EmployeeSalaryHistoryService.SetSalaryCommand;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/compensation/employees/{employeeId}/salary-history")
@RequiredArgsConstructor
public class EmployeeSalaryHistoryController {
    private final EmployeeSalaryHistoryService service;

    @GetMapping
    public ApiResult<List<SalaryHistoryView>> list(@PathVariable Long employeeId) {
        return ApiResult.ok(service.listHistory(employeeId));
    }

    @GetMapping("/effective")
    public ApiResult<SalaryHistoryView> effective(@PathVariable Long employeeId, @RequestParam LocalDate date) {
        return ApiResult.ok(service.getEffective(employeeId, date));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<SalaryHistoryView> set(@PathVariable Long employeeId, @Valid @RequestBody SetSalaryCommand request) {
        return ApiResult.ok(service.setSalary(employeeId, request));
    }
}
