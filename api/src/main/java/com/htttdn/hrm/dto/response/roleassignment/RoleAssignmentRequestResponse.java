package com.htttdn.hrm.dto.response.roleassignment;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.annotation.Nullable;

public record RoleAssignmentRequestResponse(
    Long id,
    Long accountId,
    @Nullable Long employeeId,
    @Nullable String employeeCode,
    @Nullable String employeeName,
    Long roleId,
    String roleCode,
    String roleName,
    RoleGrantPolicy grantPolicy,
    RoleScopeType scopeType,
    @Nullable Long organizationUnitId,
    @Nullable String organizationUnitName,
    @Nullable Long workLocationId,
    @Nullable String workLocationName,
    LocalDate effectiveFrom,
    @Nullable LocalDate effectiveTo,
    String reason,
    RoleAssignmentRequestStatus status,
    Long requestedByAccountId,
    String requestedByUsername,
    Instant requestedAt,
    @Nullable Long reviewedByAccountId,
    @Nullable String reviewedByUsername,
    @Nullable Instant reviewedAt,
    @Nullable String reviewNote,
    @Nullable Long cancelledByAccountId,
    @Nullable String cancelledByUsername,
    @Nullable Instant cancelledAt,
    @Nullable String cancellationReason,
    Instant updatedAt,
    @Nullable AccountRoleAssignmentResponse assignment
) {
}
