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

import com.htttdn.hrm.dto.request.worklocation.CreateWorkLocationRequest;
import com.htttdn.hrm.dto.request.worklocation.UpdateWorkLocationRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.worklocation.WorkLocationResponse;
import com.htttdn.hrm.dto.response.worklocation.WorkLocationTreeResponse;
import com.htttdn.hrm.entity.enums.LocationType;
import com.htttdn.hrm.service.WorkLocationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/work-locations")
public class WorkLocationController {

    private final WorkLocationService workLocationService;

    public WorkLocationController(WorkLocationService workLocationService) {
        this.workLocationService = workLocationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<List<WorkLocationResponse>> list(
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) LocationType locationType,
        @RequestParam(required = false) Long parentLocationId
    ) {
        return ApiResult.ok(workLocationService.list(active, locationType, parentLocationId));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<List<WorkLocationTreeResponse>> tree(
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ApiResult.ok(workLocationService.tree(includeInactive));
    }

    @GetMapping("/{locationId}")
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<WorkLocationResponse> getById(@PathVariable Long locationId) {
        return ApiResult.ok(workLocationService.getById(locationId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<WorkLocationResponse> create(
        @Valid @RequestBody CreateWorkLocationRequest request
    ) {
        return ApiResult.ok(workLocationService.create(request));
    }

    @PutMapping("/{locationId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<WorkLocationResponse> update(
        @PathVariable Long locationId,
        @Valid @RequestBody UpdateWorkLocationRequest request
    ) {
        return ApiResult.ok(workLocationService.update(locationId, request));
    }

    @DeleteMapping("/{locationId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<Void> delete(@PathVariable Long locationId) {
        workLocationService.softDelete(locationId);
        return ApiResult.ok();
    }
}
