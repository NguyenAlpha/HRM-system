package com.htttdn.hrm.dto.response.roleassignment;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;

public record RoleAssignmentRequestResponse(
    Long id,
    Long accountId,
    Long employeeId,
    String employeeCode,
    String employeeName,
    Long roleId,
    String roleCode,
    String roleName,
    RoleGrantPolicy grantPolicy,
    RoleScopeType scopeType,
    Long organizationUnitId,
    String organizationUnitName,
    Long workLocationId,
    String workLocationName,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String reason,
    RoleAssignmentRequestStatus status,
    Long requestedByAccountId,
    String requestedByUsername,
    Instant requestedAt,
    Long reviewedByAccountId,
    String reviewedByUsername,
    Instant reviewedAt,
    String reviewNote,
    Long cancelledByAccountId,
    String cancelledByUsername,
    Instant cancelledAt,
    String cancellationReason,
    Instant updatedAt,
    AccountRoleAssignmentResponse assignment
) {
}
