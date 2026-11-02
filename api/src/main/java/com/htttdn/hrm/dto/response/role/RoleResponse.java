package com.htttdn.hrm.dto.response.role;

import java.util.List;

import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.annotation.Nullable;

public record RoleResponse(
    Long id,
    String code,
    String name,
    @Nullable String description,
    Boolean isSystem,
    RoleGrantPolicy grantPolicy,
    List<RoleScopeType> allowedScopeTypes
) {
}
