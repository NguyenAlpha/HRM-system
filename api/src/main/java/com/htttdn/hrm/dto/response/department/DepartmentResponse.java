package com.htttdn.hrm.dto.response.department;

import com.htttdn.hrm.entity.enums.OrganizationUnitType;

public record DepartmentResponse(
    Long id,
    Long parentUnitId,
    String code,
    String name,
    OrganizationUnitType unitType,
    Boolean isActive
) {
}
