package com.htttdn.hrm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.organization.CreateOrganizationDirectorRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.organization.OrganizationDirectorProvisioningResponse;
import com.htttdn.hrm.service.OrganizationDirectorProvisioningService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/organization/director")
public class OrganizationDirectorAdminController {

    private final OrganizationDirectorProvisioningService directorProvisioningService;

    public OrganizationDirectorAdminController(
        OrganizationDirectorProvisioningService directorProvisioningService
    ) {
        this.directorProvisioningService = directorProvisioningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.director.provision')")
    public ApiResult<OrganizationDirectorProvisioningResponse> provision(
        @Valid @RequestBody CreateOrganizationDirectorRequest request
    ) {
        return ApiResult.ok(directorProvisioningService.provision(request));
    }
}
