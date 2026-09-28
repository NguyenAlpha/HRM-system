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

import com.htttdn.hrm.dto.request.jobposition.CreateJobPositionRequest;
import com.htttdn.hrm.dto.request.jobposition.UpdateJobPositionRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.jobposition.JobPositionResponse;
import com.htttdn.hrm.service.JobPositionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/job-positions")
public class JobPositionController {

    private final JobPositionService jobPositionService;

    public JobPositionController(JobPositionService jobPositionService) {
        this.jobPositionService = jobPositionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<List<JobPositionResponse>> list(
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) Boolean managerial
    ) {
        return ApiResult.ok(jobPositionService.list(active, managerial));
    }

    @GetMapping("/{positionId}")
    @PreAuthorize("hasAuthority('organization.read')")
    public ApiResult<JobPositionResponse> getById(@PathVariable Long positionId) {
        return ApiResult.ok(jobPositionService.getById(positionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<JobPositionResponse> create(
        @Valid @RequestBody CreateJobPositionRequest request
    ) {
        return ApiResult.ok(jobPositionService.create(request));
    }

    @PutMapping("/{positionId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<JobPositionResponse> update(
        @PathVariable Long positionId,
        @Valid @RequestBody UpdateJobPositionRequest request
    ) {
        return ApiResult.ok(jobPositionService.update(positionId, request));
    }

    @DeleteMapping("/{positionId}")
    @PreAuthorize("hasAuthority('organization.manage')")
    public ApiResult<Void> delete(@PathVariable Long positionId) {
        jobPositionService.softDelete(positionId);
        return ApiResult.ok();
    }
}
