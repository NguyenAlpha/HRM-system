package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.payroll.AllowancePolicyService;
import com.htttdn.hrm.service.payroll.AllowancePolicyService.PositionAllowanceSummary;
import com.htttdn.hrm.service.payroll.AllowancePolicyService.PositionAllowanceView;
import com.htttdn.hrm.service.payroll.AllowancePolicyService.SeniorityAllowanceView;
import com.htttdn.hrm.service.payroll.AllowancePolicyService.SetPositionAllowanceCommand;
import com.htttdn.hrm.service.payroll.AllowancePolicyService.SetSeniorityAllowancesCommand;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/compensation")
@RequiredArgsConstructor
public class AllowancePolicyController {
    private final AllowancePolicyService service;

    @GetMapping("/position-allowances")
    public ApiResult<List<PositionAllowanceSummary>> positionAllowanceSummaries() {
        return ApiResult.ok(service.listPositionAllowanceSummaries());
    }

    @GetMapping("/job-positions/{jobPositionId}/allowances")
    public ApiResult<List<PositionAllowanceView>> positionAllowances(@PathVariable Long jobPositionId) {
        return ApiResult.ok(service.listPositionAllowances(jobPositionId));
    }

    @PostMapping("/job-positions/{jobPositionId}/allowances")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<PositionAllowanceView> setPositionAllowance(@PathVariable Long jobPositionId,
        @Valid @RequestBody SetPositionAllowanceCommand command) {
        return ApiResult.ok(service.setPositionAllowance(jobPositionId, command));
    }

    @GetMapping("/seniority-allowances")
    public ApiResult<List<SeniorityAllowanceView>> seniorityAllowances() {
        return ApiResult.ok(service.listSeniorityAllowances());
    }

    @PostMapping("/seniority-allowances")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<List<SeniorityAllowanceView>> setSeniorityAllowances(
        @Valid @RequestBody SetSeniorityAllowancesCommand command) {
        return ApiResult.ok(service.setSeniorityAllowances(command));
    }
}
