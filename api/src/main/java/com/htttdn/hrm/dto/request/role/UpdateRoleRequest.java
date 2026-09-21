package com.htttdn.hrm.dto.request.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotBlank
    String name,

    String description,

    @NotNull
    Boolean isActive
) {
}
