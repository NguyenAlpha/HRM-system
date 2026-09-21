package com.htttdn.hrm.dto.request.department;

import com.htttdn.hrm.entity.enums.OrganizationUnitType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDepartmentRequest(
    Long parentUnitId,

    @NotBlank
    String code,

    @NotBlank
    String name,

    @NotNull
    OrganizationUnitType unitType
) {
}
