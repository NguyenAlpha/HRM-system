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
import com.htttdn.hrm.service.EmployeeSalaryHistoryService;
import com.htttdn.hrm.service.EmployeeSalaryHistoryService.SalaryHistoryView;
import com.htttdn.hrm.service.EmployeeSalaryHistoryService.SetSalaryCommand;

@RestController
@RequestMapping("/api/compensation/employees/{employeeId}/salary-history")
public class EmployeeSalaryHistoryController {
    private final EmployeeSalaryHistoryService service;

    public EmployeeSalaryHistoryController(EmployeeSalaryHistoryService service) {
        this.service = service;
    }

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
    public ApiResult<SalaryHistoryView> set(@PathVariable Long employeeId, @RequestBody SetSalaryCommand request) {
        return ApiResult.ok(service.setSalary(employeeId, request));
    }
}
