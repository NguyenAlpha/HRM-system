package com.htttdn.hrm.dto.response.auth;

import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.annotation.Nullable;

public record AuthRoleResponse(
    String code,
    String name,
    RoleScopeType scopeType,
    @Nullable Long organizationUnitId,
    @Nullable String organizationUnitName,
    @Nullable Long workLocationId,
    @Nullable String workLocationName
) {
}
