package com.htttdn.hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.ratelimit.RateLimitPolicyRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitStatsResponse;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitTimelineResponse;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.ratelimit.RateLimitPolicy;
import com.htttdn.hrm.ratelimit.RateLimitService;
import com.htttdn.hrm.service.RateLimitSettingsService;
import com.htttdn.hrm.timeline.TimelineRange;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/system/rate-limit")
@RequiredArgsConstructor
public class RateLimitStatsController {

    private final RateLimitService rateLimitService;
    private final RateLimitSettingsService rateLimitSettingsService;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('system.rate_limit.read')")
    public ApiResult<RateLimitStatsResponse> stats() {
        return ApiResult.ok(rateLimitService.stats());
    }

    /**
     * Requests and blocks over the last hour (per minute), day (per 15 minutes) or week (per hour);
     * {@code policy} is a policy key, or absent for all policies together.
     */
    @GetMapping("/timeline")
    @PreAuthorize("hasAuthority('system.rate_limit.read')")
    public ApiResult<RateLimitTimelineResponse> timeline(
        @RequestParam(defaultValue = "HOUR") TimelineRange range,
        @RequestParam(required = false) String policy
    ) {
        RateLimitPolicy selected = policy == null ? null : RateLimitPolicy.fromKey(policy)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RATE_LIMIT_POLICY_NOT_FOUND,
                "Rate-limit policy not found: " + policy
            ));
        return ApiResult.ok(rateLimitService.timeline(range, selected));
    }

    /** Replaces a policy's quota and on/off state; {@code policy} is its key, e.g. {@code heavy-write}. */
    @PutMapping("/policies/{policy}")
    @PreAuthorize("hasAuthority('system.rate_limit.manage')")
    public ApiResult<RateLimitStatsResponse> updatePolicy(
        @PathVariable String policy,
        @Valid @RequestBody RateLimitPolicyRequest request
    ) {
        return ApiResult.ok(rateLimitSettingsService.update(policy, request));
    }

    /** Back to the application.properties quota, switched on. */
    @DeleteMapping("/policies/{policy}")
    @PreAuthorize("hasAuthority('system.rate_limit.manage')")
    public ApiResult<RateLimitStatsResponse> restorePolicyDefault(@PathVariable String policy) {
        return ApiResult.ok(rateLimitSettingsService.restoreDefault(policy));
    }
}
