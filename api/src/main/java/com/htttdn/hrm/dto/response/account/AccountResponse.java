package com.htttdn.hrm.dto.response.account;

import java.time.Instant;

import com.htttdn.hrm.entity.enums.AccountStatus;

public record AccountResponse(
    Long id,
    Long employeeId,
    String username,
    String email,
    AccountStatus status,
    Integer failedLoginCount,
    Instant lockedUntil,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt
) {
}
