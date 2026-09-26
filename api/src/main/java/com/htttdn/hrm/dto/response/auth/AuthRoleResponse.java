package com.htttdn.hrm.dto.response.auth;

import com.htttdn.hrm.entity.enums.RoleScopeType;

public record AuthRoleResponse(
    String code,
    String name,
    RoleScopeType scopeType,
    Long organizationUnitId,
    String organizationUnitName,
    Long workLocationId,
    String workLocationName
) {
}
