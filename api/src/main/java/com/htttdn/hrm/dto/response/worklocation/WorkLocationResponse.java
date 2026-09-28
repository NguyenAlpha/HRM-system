package com.htttdn.hrm.dto.response.worklocation;

import java.time.Instant;

import com.htttdn.hrm.entity.enums.LocationType;

public record WorkLocationResponse(
    Long id,
    Long parentLocationId,
    String parentLocationName,
    String code,
    String name,
    LocationType locationType,
    String address,
    String phone,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
