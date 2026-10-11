package com.htttdn.hrm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.LeaveEntitlementService;
import com.htttdn.hrm.service.LeaveEntitlementService.CarryOverView;

/** Year-end close of annual leave: preview, then carry each employee's unused days into the next year. */
@RestController
@RequestMapping("/api/leave-entitlements/carry-over")
public class LeaveCarryOverController {

    private final LeaveEntitlementService leaveEntitlementService;

    public LeaveCarryOverController(LeaveEntitlementService leaveEntitlementService) {
        this.leaveEntitlementService = leaveEntitlementService;
    }

    @GetMapping
    public ApiResult<CarryOverView> preview(@RequestParam int fromYear) {
        return ApiResult.ok(leaveEntitlementService.previewCarryOver(fromYear));
    }

    @PostMapping
    public ApiResult<CarryOverView> apply(@RequestParam int fromYear) {
        return ApiResult.ok(leaveEntitlementService.applyCarryOver(fromYear));
    }
}
