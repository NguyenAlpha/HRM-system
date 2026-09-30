package com.htttdn.hrm.dto.response.account;

import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;

public record PermissionOverrideOptionResponse(
    Long permissionId,
    String permissionCode,
    String permissionName,
    PermissionModule module,
    String description,
    PermissionOverrideEffect effect
) {
}
