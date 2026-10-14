package com.htttdn.hrm.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.employee.CreateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.EmployeeSearchCriteria;
import com.htttdn.hrm.dto.request.employee.EndEmploymentRequest;
import com.htttdn.hrm.dto.request.employee.SoftDeleteEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateEmployeeRequest;
import com.htttdn.hrm.dto.request.employee.UpdateSelfProfileRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.dto.response.employee.EmployeeCreationResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.dto.response.employee.EmployeeSummaryResponse;
import com.htttdn.hrm.dto.response.employee.SelfProfileResponse;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.service.EmployeeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<EmployeeCreationResponse> create(
        @Valid @RequestBody CreateEmployeeRequest request
    ) {
        return ApiResult.ok(employeeService.create(request));
    }

    @GetMapping
    public ApiResult<PagedResult<EmployeeSummaryResponse>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) EmploymentStatus employmentStatus,
        @RequestParam(required = false) Long organizationUnitId,
        @RequestParam(required = false) Long positionId,
        @PageableDefault(sort = "id", size = 20) Pageable pageable
    ) {
        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria(keyword, employmentStatus, organizationUnitId, positionId);
        return ApiResult.ok(PagedResult.of(employeeService.list(criteria, pageable)));
    }

    @GetMapping("/me")
    public ApiResult<EmployeeDetailResponse> getSelf() {
        return ApiResult.ok(employeeService.getSelf());
    }

    @GetMapping("/me/profile")
    public ApiResult<SelfProfileResponse> getSelfProfile() {
        return ApiResult.ok(employeeService.getSelfProfile());
    }

    @PutMapping("/me/profile")
    public ApiResult<SelfProfileResponse> updateSelfProfile(@Valid @RequestBody UpdateSelfProfileRequest request) {
        return ApiResult.ok(employeeService.updateSelfProfile(request));
    }

    @GetMapping("/{employeeId}")
    public ApiResult<EmployeeDetailResponse> getById(@PathVariable Long employeeId) {
        return ApiResult.ok(employeeService.getById(employeeId));
    }

    @PutMapping("/{employeeId}")
    public ApiResult<EmployeeDetailResponse> update(
        @PathVariable Long employeeId,
        @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        return ApiResult.ok(employeeService.update(employeeId, request));
    }

    @PostMapping("/{employeeId}/confirm")
    public ApiResult<EmployeeDetailResponse> confirmEmployment(@PathVariable Long employeeId) {
        return ApiResult.ok(employeeService.confirmEmployment(employeeId));
    }

    @PostMapping("/{employeeId}/employment-end")
    public ApiResult<EmployeeDetailResponse> endEmployment(
        @PathVariable Long employeeId,
        @Valid @RequestBody EndEmploymentRequest request
    ) {
        return ApiResult.ok(employeeService.endEmployment(
            employeeId, request.endStatus(), request.terminationDate(), request.terminationReason()
        ));
    }

    @PostMapping("/{employeeId}/soft-delete")
    public ApiResult<Void> softDelete(
        @PathVariable Long employeeId,
        @Valid @RequestBody SoftDeleteEmployeeRequest request
    ) {
        employeeService.softDelete(employeeId, request);
        return ApiResult.ok();
    }
}
