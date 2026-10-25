package com.htttdn.hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.traffic.ApiTrafficResponse;
import com.htttdn.hrm.dto.response.traffic.ApiTrafficTimelineResponse;
import com.htttdn.hrm.service.system.ApiTrafficService;
import com.htttdn.hrm.timeline.TimelineRange;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/system/traffic")
@RequiredArgsConstructor
public class ApiTrafficController {

    private final ApiTrafficService apiTrafficService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('system.traffic.read')")
    public ApiResult<ApiTrafficResponse> stats() {
        return ApiResult.ok(apiTrafficService.stats());
    }

    /** Requests, errors and mean processing time over the last hour (per minute), day (per 15 minutes) or week (per hour). */
    @GetMapping("/timeline")
    @PreAuthorize("hasAuthority('system.traffic.read')")
    public ApiResult<ApiTrafficTimelineResponse> timeline(@RequestParam(defaultValue = "HOUR") TimelineRange range) {
        return ApiResult.ok(apiTrafficService.timeline(range));
    }
}
