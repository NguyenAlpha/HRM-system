package com.htttdn.hrm.dto.request.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePermissionRequest(
    @NotBlank
    @Size(max = 150)
    String name,

    @NotBlank
    String description,

    @NotNull
    Boolean isActive
) {
}
