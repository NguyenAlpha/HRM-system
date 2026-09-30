package com.htttdn.hrm.dto.response.account;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.PermissionOverrideStatus;

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
    LocalDate effectiveTo,
    String reason,
    Long grantedByAccountId,
    String grantedByUsername,
    Instant createdAt,
    Long revokedByAccountId,
    String revokedByUsername,
    Instant revokedAt,
    String revocationReason,
    PermissionOverrideStatus status
) {
}
