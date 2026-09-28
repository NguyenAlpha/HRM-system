package com.htttdn.hrm.dto.response.jobposition;

import java.time.Instant;

public record JobPositionResponse(
    Long id,
    String code,
    String title,
    String description,
    Boolean isManagerial,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
