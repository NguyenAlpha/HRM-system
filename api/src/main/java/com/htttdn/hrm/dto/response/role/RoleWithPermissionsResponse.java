package com.htttdn.hrm.dto.response.role;

import java.util.List;

import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;

public record RoleWithPermissionsResponse(
    Long id,
    String code,
    String name,
    String description,
    Boolean isSystem,
    RoleGrantPolicy grantPolicy,
    List<PermissionResponse> permissions
) {
}
