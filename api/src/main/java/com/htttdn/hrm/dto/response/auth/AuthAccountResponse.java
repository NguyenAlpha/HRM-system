package com.htttdn.hrm.dto.response.auth;

import java.util.List;

import com.htttdn.hrm.entity.enums.AccountStatus;

public record AuthAccountResponse(
    Long accountId,
    String username,
    String email,
    AccountStatus status,
    AuthEmployeeResponse employee,
    List<AuthRoleResponse> roles,
    List<AuthPermissionResponse> permissions
) {
}
