package com.htttdn.hrm.dto.request.role;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(
    @NotBlank
    String code,

    @NotBlank
    String name,

    String description
) {
}
