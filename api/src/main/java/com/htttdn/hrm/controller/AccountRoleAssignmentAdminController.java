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

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.account.RevokeAccountRoleRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.AccountRoleAssignmentAdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/accounts/{accountId}/role-assignments")
public class AccountRoleAssignmentAdminController {

    private final AccountRoleAssignmentAdminService roleAssignmentAdminService;

    public AccountRoleAssignmentAdminController(AccountRoleAssignmentAdminService roleAssignmentAdminService) {
        this.roleAssignmentAdminService = roleAssignmentAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('account.read')")
    public ApiResult<List<AccountRoleAssignmentResponse>> list(@PathVariable Long accountId) {
        return ApiResult.ok(roleAssignmentAdminService.list(accountId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('account.role.assign')")
    public ApiResult<AccountRoleAssignmentResponse> assign(
        @PathVariable Long accountId,
        @Valid @RequestBody AssignAccountRoleRequest request
    ) {
        return ApiResult.ok(roleAssignmentAdminService.assign(accountId, request));
    }

    @PostMapping("/{assignmentId}/revoke")
    @PreAuthorize("hasAuthority('account.role.assign')")
    public ApiResult<AccountRoleAssignmentResponse> revoke(
        @PathVariable Long accountId,
        @PathVariable Long assignmentId,
        @Valid @RequestBody RevokeAccountRoleRequest request
    ) {
        return ApiResult.ok(roleAssignmentAdminService.revoke(accountId, assignmentId, request));
    }
}
