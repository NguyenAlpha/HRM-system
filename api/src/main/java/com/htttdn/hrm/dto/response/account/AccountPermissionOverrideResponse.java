package com.htttdn.hrm.dto.response.account;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.PermissionOverrideStatus;

import jakarta.annotation.Nullable;

public record AccountPermissionOverrideResponse(
    Long id,
    Long accountRoleAssignmentId,
    Long accountId,
    Long permissionId,
    String permissionCode,
    String permissionName,
    PermissionModule module,
    PermissionOverrideEffect effect,
    LocalDate effectiveFrom,
    @Nullable LocalDate effectiveTo,
    String reason,
    Long grantedByAccountId,
    String grantedByUsername,
    Instant createdAt,
    @Nullable Long revokedByAccountId,
    @Nullable String revokedByUsername,
    @Nullable Instant revokedAt,
    @Nullable String revocationReason,
    PermissionOverrideStatus status
) {
}
