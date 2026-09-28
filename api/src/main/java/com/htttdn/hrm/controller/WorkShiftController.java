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

import com.htttdn.hrm.dto.request.workshift.CreateWorkShiftRequest;
import com.htttdn.hrm.dto.request.workshift.UpdateWorkShiftRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.workshift.WorkShiftResponse;
import com.htttdn.hrm.service.WorkShiftService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/work-shifts")
public class WorkShiftController {

    private final WorkShiftService workShiftService;

    public WorkShiftController(WorkShiftService workShiftService) {
        this.workShiftService = workShiftService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<List<WorkShiftResponse>> list(
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) Boolean crossesMidnight
    ) {
        return ApiResult.ok(workShiftService.list(active, crossesMidnight));
    }

    @GetMapping("/{shiftId}")
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<WorkShiftResponse> getById(@PathVariable Long shiftId) {
        return ApiResult.ok(workShiftService.getById(shiftId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<WorkShiftResponse> create(
        @Valid @RequestBody CreateWorkShiftRequest request
    ) {
        return ApiResult.ok(workShiftService.create(request));
    }

    @PutMapping("/{shiftId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<WorkShiftResponse> update(
        @PathVariable Long shiftId,
        @Valid @RequestBody UpdateWorkShiftRequest request
    ) {
        return ApiResult.ok(workShiftService.update(shiftId, request));
    }

    @DeleteMapping("/{shiftId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<Void> delete(@PathVariable Long shiftId) {
        workShiftService.softDelete(shiftId);
        return ApiResult.ok();
    }
}
