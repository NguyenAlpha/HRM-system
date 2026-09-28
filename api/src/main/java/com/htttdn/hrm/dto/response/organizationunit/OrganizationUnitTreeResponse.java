package com.htttdn.hrm.dto.response.organizationunit;

import java.util.List;

import com.htttdn.hrm.entity.enums.OrganizationUnitType;

public record OrganizationUnitTreeResponse(
    Long id,
    String code,
    String name,
    OrganizationUnitType unitType,
    Boolean isActive,
    List<OrganizationUnitTreeResponse> children
) {
}
