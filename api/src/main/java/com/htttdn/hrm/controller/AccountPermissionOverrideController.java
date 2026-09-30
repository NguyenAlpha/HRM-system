package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.account.CreatePermissionOverrideRequest;
import com.htttdn.hrm.dto.request.account.RevokePermissionOverrideRequest;
import com.htttdn.hrm.dto.response.account.AccountPermissionOverrideResponse;
import com.htttdn.hrm.dto.response.account.PermissionOverrideOptionResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.AccountPermissionOverrideService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts/{accountId}/role-assignments/{assignmentId}/permission-overrides")
public class AccountPermissionOverrideController {

    private final AccountPermissionOverrideService permissionOverrideService;

    public AccountPermissionOverrideController(AccountPermissionOverrideService permissionOverrideService) {
        this.permissionOverrideService = permissionOverrideService;
    }

    @GetMapping
    public ApiResult<List<AccountPermissionOverrideResponse>> list(
        @PathVariable Long accountId,
        @PathVariable Long assignmentId
    ) {
        return ApiResult.ok(permissionOverrideService.list(accountId, assignmentId));
    }

    @GetMapping("/available-permissions")
    public ApiResult<List<PermissionOverrideOptionResponse>> availablePermissions(
        @PathVariable Long accountId,
        @PathVariable Long assignmentId
    ) {
        return ApiResult.ok(permissionOverrideService.availablePermissions(accountId, assignmentId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<AccountPermissionOverrideResponse> create(
        @PathVariable Long accountId,
        @PathVariable Long assignmentId,
        @Valid @RequestBody CreatePermissionOverrideRequest request
    ) {
        return ApiResult.ok(permissionOverrideService.create(accountId, assignmentId, request));
    }

    @PostMapping("/{overrideId}/revoke")
    public ApiResult<AccountPermissionOverrideResponse> revoke(
        @PathVariable Long accountId,
        @PathVariable Long assignmentId,
        @PathVariable Long overrideId,
        @Valid @RequestBody RevokePermissionOverrideRequest request
    ) {
        return ApiResult.ok(permissionOverrideService.revoke(accountId, assignmentId, overrideId, request));
    }
}
