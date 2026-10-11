package com.htttdn.hrm.dto.response.worklocation;

import java.time.Instant;

import com.htttdn.hrm.entity.enums.LocationType;

import jakarta.annotation.Nullable;

public record WorkLocationResponse(
    Long id,
    @Nullable Long parentLocationId,
    @Nullable String parentLocationName,
    String code,
    String name,
    LocationType locationType,
    String address,
    @Nullable String phone,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
