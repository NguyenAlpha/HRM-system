package com.htttdn.hrm.dto.response.auth;

import java.util.List;

import com.htttdn.hrm.entity.enums.AccountStatus;

public record AuthAccountResponse(
    Long id,
    Long employeeId,
    String username,
    String email,
    AccountStatus status,
    List<String> roles,
    List<String> permissions
) {
}
