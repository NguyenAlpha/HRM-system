package com.htttdn.hrm.dto.request.attendance;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

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
    Instant checkOutAt
) {
}
