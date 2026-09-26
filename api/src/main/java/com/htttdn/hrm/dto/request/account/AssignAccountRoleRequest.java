package com.htttdn.hrm.dto.request.account;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssignAccountRoleRequest(
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
    String roleCode,

    @NotNull
    RoleScopeType scopeType,

    Long organizationUnitId,

    Long workLocationId,

    @NotNull
    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    @NotBlank
    @Size(max = 500)
    String reason
) {
}
