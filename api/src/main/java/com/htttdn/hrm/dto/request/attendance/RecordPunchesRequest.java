package com.htttdn.hrm.dto.request.attendance;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record RecordPunchesRequest(
    @NotNull
    Long employeeId,

    @NotNull
    LocalDate workDate,

    @NotNull
    @PastOrPresent
    Instant checkInAt,

    @NotNull
    @PastOrPresent
    Instant checkOutAt,

    /** Why HR entered or corrected the punches; null when they come from the time clock. */
    @Nullable
    @Size(max = 100)
    String note
) {
}
