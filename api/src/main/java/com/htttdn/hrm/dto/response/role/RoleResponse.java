package com.htttdn.hrm.dto.response.role;

import com.htttdn.hrm.entity.enums.RoleGrantPolicy;

import jakarta.annotation.Nullable;

public record RoleResponse(
    Long id,
    String code,
    String name,
    @Nullable String description,
    Boolean isSystem,
    RoleGrantPolicy grantPolicy
) {
}
