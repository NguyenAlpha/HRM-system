package com.htttdn.hrm.dto.response.auth;

import java.util.List;

import com.htttdn.hrm.entity.enums.AccountStatus;

import jakarta.annotation.Nullable;

public record AuthAccountResponse(
    Long accountId,
    String username,
    String email,
    AccountStatus status,
    @Nullable AuthEmployeeResponse employee,
    List<AuthRoleResponse> roles,
    List<AuthPermissionResponse> permissions
) {
}
