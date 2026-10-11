package com.htttdn.hrm.dto.response.role;

import java.util.List;

import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;

import jakarta.annotation.Nullable;

public record RoleWithPermissionsResponse(
    Long id,
    String code,
    String name,
    @Nullable String description,
    Boolean isSystem,
    RoleGrantPolicy grantPolicy,
    List<PermissionResponse> permissions
) {
}
