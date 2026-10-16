package com.htttdn.hrm.dto.request.role;

import com.htttdn.hrm.entity.enums.RoleGrantPolicy;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "(?!ROLE_)[A-Z][A-Z0-9_]*", message = "Use uppercase role codes without the ROLE_ prefix")
    String code,

    @NotBlank
    @Size(max = 30)
    String name,

    @Nullable
    String description,

    @NotNull
    RoleGrantPolicy grantPolicy
) {
}
