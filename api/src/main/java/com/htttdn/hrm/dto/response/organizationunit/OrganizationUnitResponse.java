package com.htttdn.hrm.dto.response.organizationunit;

import java.time.Instant;

import com.htttdn.hrm.entity.enums.OrganizationUnitType;

public record OrganizationUnitResponse(
    Long id,
    Long parentUnitId,
    String parentUnitName,
    String code,
    String name,
    OrganizationUnitType unitType,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
