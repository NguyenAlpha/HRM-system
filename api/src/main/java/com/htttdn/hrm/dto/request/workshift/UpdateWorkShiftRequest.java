package com.htttdn.hrm.dto.request.workshift;

import java.time.LocalTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWorkShiftRequest(
    @NotBlank
    @Size(max = 100)
    String name,

    @NotNull
    LocalTime startTime,

    @NotNull
    LocalTime endTime,

    @NotNull
    @Min(0)
    @Max(1439)
    Integer breakMinutes,

    /** Required when breakMinutes is positive, null otherwise. */
    @Nullable
    LocalTime breakStartTime,

    @NotNull
    @Min(1)
    @Max(1440)
    Integer standardWorkMinutes,

    @NotNull
    @Min(0)
    @Max(1440)
    Integer graceLateMinutes,

    @NotNull
    Boolean crossesMidnight,

    @NotNull
    Boolean isActive
) {
}
