package com.htttdn.hrm.dto.request.permission;

import com.htttdn.hrm.entity.enums.PermissionModule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+", message = "Use lowercase permission codes separated by dots")
    String code,

    @NotBlank
    @Size(max = 150)
    String name,

    @NotNull
    PermissionModule module,

    @NotBlank
    String description
) {
}
