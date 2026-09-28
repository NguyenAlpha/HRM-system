package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.organizationunit.CreateOrganizationUnitRequest;
import com.htttdn.hrm.dto.request.organizationunit.UpdateOrganizationUnitRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.organizationunit.OrganizationUnitResponse;
import com.htttdn.hrm.dto.response.organizationunit.OrganizationUnitTreeResponse;
import com.htttdn.hrm.entity.enums.OrganizationUnitType;
import com.htttdn.hrm.service.OrganizationUnitService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organization-units")
public class OrganizationUnitController {

    private final OrganizationUnitService organizationUnitService;

    public OrganizationUnitController(OrganizationUnitService organizationUnitService) {
        this.organizationUnitService = organizationUnitService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<List<OrganizationUnitResponse>> list(
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) OrganizationUnitType unitType,
        @RequestParam(required = false) Long parentUnitId
    ) {
        return ApiResult.ok(organizationUnitService.list(active, unitType, parentUnitId));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<List<OrganizationUnitTreeResponse>> tree(
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ApiResult.ok(organizationUnitService.tree(includeInactive));
    }

    @GetMapping("/{unitId}")
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<OrganizationUnitResponse> getById(@PathVariable Long unitId) {
        return ApiResult.ok(organizationUnitService.getById(unitId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<OrganizationUnitResponse> create(
        @Valid @RequestBody CreateOrganizationUnitRequest request
    ) {
        return ApiResult.ok(organizationUnitService.create(request));
    }

    @PutMapping("/{unitId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<OrganizationUnitResponse> update(
        @PathVariable Long unitId,
        @Valid @RequestBody UpdateOrganizationUnitRequest request
    ) {
        return ApiResult.ok(organizationUnitService.update(unitId, request));
    }

    @DeleteMapping("/{unitId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<Void> delete(@PathVariable Long unitId) {
        organizationUnitService.softDelete(unitId);
        return ApiResult.ok();
    }
}
