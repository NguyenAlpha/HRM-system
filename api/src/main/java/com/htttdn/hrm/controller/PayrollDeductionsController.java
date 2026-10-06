package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.PayrollDeductionsService;
import com.htttdn.hrm.service.PayrollDeductionsService.Dependent;
import com.htttdn.hrm.service.PayrollDeductionsService.DependentCommand;
import com.htttdn.hrm.service.PayrollDeductionsService.EndDependentCommand;
import com.htttdn.hrm.service.PayrollDeductionsService.Profile;
import com.htttdn.hrm.service.PayrollDeductionsService.ProfileCommand;

@RestController
@RequestMapping("/api/compensation/employees/{employeeId}")
public class PayrollDeductionsController {
    private final PayrollDeductionsService service;

    public PayrollDeductionsController(PayrollDeductionsService service) {
        this.service = service;
    }

    @GetMapping("/payroll-profiles")
    public ApiResult<List<Profile>> profiles(@PathVariable Long employeeId) {
        return ApiResult.ok(service.profiles(employeeId));
    }

    @PostMapping("/payroll-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<Profile> setProfile(@PathVariable Long employeeId, @RequestBody ProfileCommand command) {
        return ApiResult.ok(service.setProfile(employeeId, command));
    }

    @GetMapping("/tax-dependents")
    public ApiResult<List<Dependent>> dependents(@PathVariable Long employeeId) {
        return ApiResult.ok(service.dependents(employeeId));
    }

    @PostMapping("/tax-dependents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<Dependent> addDependent(@PathVariable Long employeeId, @RequestBody DependentCommand command) {
        return ApiResult.ok(service.addDependent(employeeId, command));
    }

    @PutMapping("/tax-dependents/{dependentId}/end")
    public ApiResult<Dependent> endDependent(@PathVariable Long employeeId, @PathVariable Long dependentId,
        @RequestBody EndDependentCommand command) {
        return ApiResult.ok(service.endDependent(employeeId, dependentId, command.effectiveTo()));
    }
}
