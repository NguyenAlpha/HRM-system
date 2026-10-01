package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.employee.EmployeeAssignmentResponse;
import com.htttdn.hrm.service.EmployeeAssignmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees/{employeeId}/assignments")
public class EmployeeAssignmentController {

    private final EmployeeAssignmentService employeeAssignmentService;

    public EmployeeAssignmentController(EmployeeAssignmentService employeeAssignmentService) {
        this.employeeAssignmentService = employeeAssignmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.assignment.read')")
    public ApiResult<List<EmployeeAssignmentResponse>> list(@PathVariable Long employeeId) {
        return ApiResult.ok(employeeAssignmentService.list(employeeId));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('employee.assignment.read')")
    public ApiResult<EmployeeAssignmentResponse> getCurrent(@PathVariable Long employeeId) {
        return ApiResult.ok(employeeAssignmentService.getCurrent(employeeId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('employee.assignment.manage')")
    public ApiResult<EmployeeAssignmentResponse> assign(
        @PathVariable Long employeeId,
        @Valid @RequestBody AssignEmployeeRequest request
    ) {
        return ApiResult.ok(employeeAssignmentService.assign(employeeId, request));
    }
}
