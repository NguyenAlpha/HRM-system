package com.htttdn.hrm.dto.response.role;

import java.util.List;

import com.htttdn.hrm.dto.response.permission.PermissionResponse;

public record RoleWithPermissionsResponse(
    Long id,
    String code,
    String name,
    String description,
    Boolean isSystem,
    List<PermissionResponse> permissions
) {
}
