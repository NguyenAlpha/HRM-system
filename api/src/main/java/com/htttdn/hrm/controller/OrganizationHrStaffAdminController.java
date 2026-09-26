package com.htttdn.hrm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.organization.CreateOrganizationHrStaffRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.organization.OrganizationHrStaffProvisioningResponse;
import com.htttdn.hrm.service.OrganizationHrStaffProvisioningService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/organization/hr-staff")
public class OrganizationHrStaffAdminController {

    private final OrganizationHrStaffProvisioningService hrStaffProvisioningService;

    public OrganizationHrStaffAdminController(
        OrganizationHrStaffProvisioningService hrStaffProvisioningService
    ) {
        this.hrStaffProvisioningService = hrStaffProvisioningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.hr_staff.bootstrap')")
    public ApiResult<OrganizationHrStaffProvisioningResponse> provision(
        @Valid @RequestBody CreateOrganizationHrStaffRequest request
    ) {
        return ApiResult.ok(hrStaffProvisioningService.provision(request));
    }
}
