package com.htttdn.hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitStatsResponse;
import com.htttdn.hrm.ratelimit.RateLimitService;

@RestController
@RequestMapping("/api/system/rate-limit")
public class RateLimitStatsController {

    private final RateLimitService rateLimitService;

    public RateLimitStatsController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('system.rate_limit.read')")
    public ApiResult<RateLimitStatsResponse> stats() {
        return ApiResult.ok(rateLimitService.stats());
    }
}
