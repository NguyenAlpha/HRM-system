package com.htttdn.hrm.dto.request.account;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
    @NotNull
    Long roleId,

    @NotNull
    RoleScopeType scopeType,

    Long organizationUnitId,

    Long workLocationId,

    @NotNull
    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    @NotNull
    Long grantedByAccountId,

    String reason
) {
}
