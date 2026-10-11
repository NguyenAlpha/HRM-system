package com.htttdn.hrm.dto.response.account;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.annotation.Nullable;

public record AccountRoleAssignmentResponse(
    Long id,
    Long accountId,
    Long roleId,
    String roleCode,
    String roleName,
    RoleScopeType scopeType,
    @Nullable Long organizationUnitId,
    @Nullable Long workLocationId,
    LocalDate effectiveFrom,
    @Nullable LocalDate effectiveTo,
    Long grantedByAccountId,
    @Nullable String reason,
    Instant createdAt,
    @Nullable Long revokedByAccountId,
    @Nullable Instant revokedAt,
    @Nullable String revocationReason
) {
}
