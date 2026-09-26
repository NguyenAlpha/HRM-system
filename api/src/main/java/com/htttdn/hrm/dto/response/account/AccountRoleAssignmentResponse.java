package com.htttdn.hrm.dto.response.account;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.RoleScopeType;

public record AccountRoleAssignmentResponse(
    Long id,
    Long accountId,
    Long roleId,
    String roleCode,
    String roleName,
    RoleScopeType scopeType,
    Long organizationUnitId,
    Long workLocationId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Long grantedByAccountId,
    String reason,
    Instant createdAt,
    Long revokedByAccountId,
    Instant revokedAt,
    String revocationReason
) {
}
