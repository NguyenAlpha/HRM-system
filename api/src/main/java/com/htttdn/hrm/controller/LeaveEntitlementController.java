package com.htttdn.hrm.controller;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.leave.LeaveEntitlementService;
import com.htttdn.hrm.service.leave.LeaveEntitlementService.BalanceView;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/leave-entitlements/employees/{employeeId}")
@RequiredArgsConstructor
public class LeaveEntitlementController {


    private final LeaveEntitlementService leaveEntitlementService;
    private final Clock clock;

    @GetMapping
    public ApiResult<BalanceView> getBalance(
        @PathVariable Long employeeId,
        @RequestParam(required = false) @Min(2000) @Max(9999) Integer year
    ) {
        return ApiResult.ok(leaveEntitlementService.getBalance(employeeId, resolveYear(year)));
    }

    private int resolveYear(Integer year) {
        return year != null ? year : LocalDate.now(clock).getYear();
    }
}
