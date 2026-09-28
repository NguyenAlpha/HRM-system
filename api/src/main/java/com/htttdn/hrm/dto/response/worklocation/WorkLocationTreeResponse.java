package com.htttdn.hrm.dto.response.worklocation;

import java.util.List;

import com.htttdn.hrm.entity.enums.LocationType;

public record WorkLocationTreeResponse(
    Long id,
    String code,
    String name,
    LocationType locationType,
    String address,
    String phone,
    Boolean isActive,
    List<WorkLocationTreeResponse> children
) {
}
