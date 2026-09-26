package com.htttdn.hrm.dto.response.auth;

import com.htttdn.hrm.entity.enums.PermissionModule;

public record AuthPermissionResponse(
    String code,
    String name,
    PermissionModule module
) {
}
