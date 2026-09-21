package com.htttdn.hrm.dto.request.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdatePermissionRequest(
    @NotBlank
    String description,

    @NotNull
    Boolean isActive
) {
}
