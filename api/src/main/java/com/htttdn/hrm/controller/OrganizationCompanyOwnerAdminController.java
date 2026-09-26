package com.htttdn.hrm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.organization.CreateOrganizationCompanyOwnerRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.organization.OrganizationCompanyOwnerProvisioningResponse;
import com.htttdn.hrm.service.OrganizationCompanyOwnerProvisioningService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/organization/company-owner")
public class OrganizationCompanyOwnerAdminController {

    private final OrganizationCompanyOwnerProvisioningService companyOwnerProvisioningService;

    public OrganizationCompanyOwnerAdminController(
        OrganizationCompanyOwnerProvisioningService companyOwnerProvisioningService
    ) {
        this.companyOwnerProvisioningService = companyOwnerProvisioningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.company_owner.bootstrap')")
    public ApiResult<OrganizationCompanyOwnerProvisioningResponse> provision(
        @Valid @RequestBody CreateOrganizationCompanyOwnerRequest request
    ) {
        return ApiResult.ok(companyOwnerProvisioningService.provision(request));
    }
}
