package com.htttdn.hrm.dto.request.permission;

import com.htttdn.hrm.entity.enums.PermissionModule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePermissionRequest(
    @NotBlank
    String code,

    @NotNull
    PermissionModule module,

    @NotBlank
    String description
) {
}
