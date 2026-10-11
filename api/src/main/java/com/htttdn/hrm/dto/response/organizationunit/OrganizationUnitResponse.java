package com.htttdn.hrm.dto.response.organizationunit;

import java.time.Instant;

import com.htttdn.hrm.entity.enums.OrganizationUnitType;

import jakarta.annotation.Nullable;

public record OrganizationUnitResponse(
    Long id,
    @Nullable Long parentUnitId,
    @Nullable String parentUnitName,
    String code,
    String name,
    OrganizationUnitType unitType,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
