package com.htttdn.hrm.controller;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.leave.AdjustLeaveEntitlementRequest;
import com.htttdn.hrm.dto.request.leave.SetCarriedOverLeaveRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.LeaveEntitlementService;
import com.htttdn.hrm.service.LeaveEntitlementService.BalanceView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/leave-entitlements/employees/{employeeId}")
public class LeaveEntitlementController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LeaveEntitlementService leaveEntitlementService;

    public LeaveEntitlementController(LeaveEntitlementService leaveEntitlementService) {
        this.leaveEntitlementService = leaveEntitlementService;
    }

    @GetMapping
    public ApiResult<BalanceView> getBalance(
        @PathVariable Long employeeId,
        @RequestParam(required = false) Integer year
    ) {
        return ApiResult.ok(leaveEntitlementService.getBalance(employeeId, resolveYear(year)));
    }

    @PutMapping("/adjustment")
    public ApiResult<BalanceView> adjust(
        @PathVariable Long employeeId,
        @RequestParam(required = false) Integer year,
        @Valid @RequestBody AdjustLeaveEntitlementRequest request
    ) {
        return ApiResult.ok(leaveEntitlementService.adjust(
            employeeId, resolveYear(year), request.adjustmentMinutes(), request.reason()
        ));
    }

    @PutMapping("/carried-over")
    public ApiResult<BalanceView> setCarriedOver(
        @PathVariable Long employeeId,
        @RequestParam(required = false) Integer year,
        @Valid @RequestBody SetCarriedOverLeaveRequest request
    ) {
        return ApiResult.ok(leaveEntitlementService.setCarriedOver(
            employeeId, resolveYear(year), request.carriedOverMinutes(), request.reason()
        ));
    }

    private int resolveYear(Integer year) {
        return year != null ? year : LocalDate.now(BUSINESS_ZONE).getYear();
    }
}
