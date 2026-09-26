package com.htttdn.hrm.dto.response.permission;

import com.htttdn.hrm.entity.enums.PermissionModule;

public record PermissionResponse(
    Long id,
    String code,
    String name,
    PermissionModule module,
    String description,
    Boolean isActive
) {
}
