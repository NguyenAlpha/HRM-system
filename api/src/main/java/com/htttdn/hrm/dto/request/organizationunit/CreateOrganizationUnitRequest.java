package com.htttdn.hrm.dto.request.organizationunit;

import com.htttdn.hrm.entity.enums.OrganizationUnitType;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationUnitRequest(
    @Nullable
    Long parentUnitId,

    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "^[A-Z][A-Z0-9_-]*$")
    String code,

    @NotBlank
    @Size(max = 30)
    String name,

    @NotNull
    OrganizationUnitType unitType
) {
}
