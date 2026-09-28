package com.htttdn.hrm.dto.response.workshift;

import java.time.Instant;
import java.time.LocalTime;

public record WorkShiftResponse(
    Long id,
    String code,
    String name,
    LocalTime startTime,
    LocalTime endTime,
    Integer breakMinutes,
    Integer standardWorkMinutes,
    Integer graceLateMinutes,
    Boolean crossesMidnight,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
