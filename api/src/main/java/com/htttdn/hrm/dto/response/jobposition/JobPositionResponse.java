package com.htttdn.hrm.dto.response.jobposition;

import java.time.Instant;

import jakarta.annotation.Nullable;

public record JobPositionResponse(
    Long id,
    String code,
    String title,
    @Nullable String description,
    Boolean isManagerial,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
