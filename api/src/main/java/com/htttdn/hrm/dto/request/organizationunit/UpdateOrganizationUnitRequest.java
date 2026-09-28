package com.htttdn.hrm.dto.request.organizationunit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationUnitRequest(
    Long parentUnitId,

    @NotBlank
    @Size(max = 150)
    String name,

    @NotNull
    Boolean isActive
) {
}
