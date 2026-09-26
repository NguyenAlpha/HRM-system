package com.htttdn.hrm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.organization.CreateOrganizationExecutiveRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.organization.OrganizationExecutiveProvisioningResponse;
import com.htttdn.hrm.service.OrganizationExecutiveProvisioningService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/organization/executive")
public class OrganizationExecutiveAdminController {

    private final OrganizationExecutiveProvisioningService executiveProvisioningService;

    public OrganizationExecutiveAdminController(
        OrganizationExecutiveProvisioningService executiveProvisioningService
    ) {
        this.executiveProvisioningService = executiveProvisioningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.executive.provision')")
    public ApiResult<OrganizationExecutiveProvisioningResponse> provision(
        @Valid @RequestBody CreateOrganizationExecutiveRequest request
    ) {
        return ApiResult.ok(executiveProvisioningService.provision(request));
    }
}
