package com.htttdn.hrm.dto.request.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
    @NotBlank
    @Size(max = 150)
    String name,

    String description,

    @NotNull
    Boolean isActive
) {
}
