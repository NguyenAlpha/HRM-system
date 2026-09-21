package com.htttdn.hrm.dto.response.account;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.RoleScopeType;

public record AccountRoleAssignmentResponse(
    Long id,
    Long accountId,
    Long roleId,
    String roleCode,
    RoleScopeType scopeType,
    Long organizationUnitId,
    Long workLocationId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {
}
