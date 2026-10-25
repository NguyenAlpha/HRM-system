package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.roleassignment.ApproveRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CancelRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleRevocationRequest;
import com.htttdn.hrm.dto.request.roleassignment.RejectRoleAssignmentRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentOptionResponse;
import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentRequestResponse;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.service.rbac.RoleAssignmentOptionService;
import com.htttdn.hrm.service.rbac.RoleAssignmentRequestService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/role-assignment-requests")
@RequiredArgsConstructor
public class RoleAssignmentRequestController {

    private final RoleAssignmentRequestService requestService;
    private final RoleAssignmentOptionService optionService;

    @GetMapping("/available-roles")
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public ApiResult<List<RoleAssignmentOptionResponse>> listAvailableRoles() {
        return ApiResult.ok(optionService.listAvailableRoles());
    }

    @GetMapping("/revocable-assignments")
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public ApiResult<List<AccountRoleAssignmentResponse>> listRevocableAssignments(@RequestParam Long accountId) {
        return ApiResult.ok(requestService.listRevocableAssignments(accountId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public ApiResult<RoleAssignmentRequestResponse> create(
        @Valid @RequestBody CreateRoleAssignmentRequest request
    ) {
        return ApiResult.ok(requestService.create(request));
    }

    @PostMapping("/revocations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public ApiResult<RoleAssignmentRequestResponse> createRevocation(
        @Valid @RequestBody CreateRoleRevocationRequest request
    ) {
        return ApiResult.ok(requestService.createRevocation(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('role.assignment.request', 'role.assignment.approve')")
    public ApiResult<PagedResult<RoleAssignmentRequestResponse>> list(
        @RequestParam(required = false) RoleAssignmentRequestStatus status,
        @PageableDefault(
            sort = "requestedAt",
            direction = Sort.Direction.DESC,
            size = 20
        ) Pageable pageable
    ) {
        return ApiResult.ok(PagedResult.of(requestService.list(status, pageable)));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyAuthority('role.assignment.request', 'role.assignment.approve')")
    public ApiResult<RoleAssignmentRequestResponse> getById(@PathVariable Long requestId) {
        return ApiResult.ok(requestService.getById(requestId));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAuthority('role.assignment.approve')")
    public ApiResult<RoleAssignmentRequestResponse> approve(
        @PathVariable Long requestId,
        @Valid @RequestBody ApproveRoleAssignmentRequest request
    ) {
        return ApiResult.ok(requestService.approve(requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAuthority('role.assignment.approve')")
    public ApiResult<RoleAssignmentRequestResponse> reject(
        @PathVariable Long requestId,
        @Valid @RequestBody RejectRoleAssignmentRequest request
    ) {
        return ApiResult.ok(requestService.reject(requestId, request));
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("hasAuthority('role.assignment.request')")
    public ApiResult<RoleAssignmentRequestResponse> cancel(
        @PathVariable Long requestId,
        @Valid @RequestBody CancelRoleAssignmentRequest request
    ) {
        return ApiResult.ok(requestService.cancel(requestId, request));
    }
}
