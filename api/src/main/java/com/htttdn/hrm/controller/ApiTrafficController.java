package com.htttdn.hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.traffic.ApiTrafficResponse;
import com.htttdn.hrm.service.ApiTrafficService;

@RestController
@RequestMapping("/api/system/traffic")
public class ApiTrafficController {

    private final ApiTrafficService apiTrafficService;

    public ApiTrafficController(ApiTrafficService apiTrafficService) {
        this.apiTrafficService = apiTrafficService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('system.traffic.read')")
    public ApiResult<ApiTrafficResponse> stats() {
        return ApiResult.ok(apiTrafficService.stats());
    }
}
