package com.htttdn.hrm.dto.response.account;

import java.time.Instant;
import java.util.List;

import com.htttdn.hrm.entity.enums.AccountStatus;

import jakarta.annotation.Nullable;

public record AccountResponse(
    Long id,
    @Nullable Long employeeId,
    @Nullable String employeeCode,
    @Nullable String employeeFullName,
    String username,
    String email,
    AccountStatus status,
    /** Codes of the roles the account holds and that are not revoked, sorted. */
    List<String> roleCodes,
    Integer failedLoginCount,
    @Nullable Instant lockedUntil,
    @Nullable Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt
) {
}
