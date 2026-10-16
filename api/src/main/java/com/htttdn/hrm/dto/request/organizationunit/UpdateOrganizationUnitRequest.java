package com.htttdn.hrm.dto.request.organizationunit;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationUnitRequest(
    @Nullable
    Long parentUnitId,

    @NotBlank
    @Size(max = 30)
    String name,

    @NotNull
    Boolean isActive
) {
}
