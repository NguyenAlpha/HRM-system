package com.htttdn.hrm.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.organization.CompanyProfileRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.organization.CompanyProfileResponse;
import com.htttdn.hrm.service.CompanyProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/company")
public class CompanyProfileController {

    private final CompanyProfileService service;

    public CompanyProfileController(CompanyProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<CompanyProfileResponse> get() {
        return ApiResult.ok(service.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('company.manage')")
    public ApiResult<CompanyProfileResponse> update(@Valid @RequestBody CompanyProfileRequest request) {
        return ApiResult.ok(service.update(request));
    }
}
