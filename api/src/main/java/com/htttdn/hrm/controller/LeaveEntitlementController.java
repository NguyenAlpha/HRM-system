package com.htttdn.hrm.controller;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.leave.LeaveEntitlementService;
import com.htttdn.hrm.service.leave.LeaveEntitlementService.BalanceView;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/leave-entitlements/employees/{employeeId}")
@RequiredArgsConstructor
public class LeaveEntitlementController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LeaveEntitlementService leaveEntitlementService;

    @GetMapping
    public ApiResult<BalanceView> getBalance(
        @PathVariable Long employeeId,
        @RequestParam(required = false) Integer year
    ) {
        return ApiResult.ok(leaveEntitlementService.getBalance(employeeId, resolveYear(year)));
    }

    private int resolveYear(Integer year) {
        return year != null ? year : LocalDate.now(BUSINESS_ZONE).getYear();
    }
}
