package com.htttdn.hrm.dto.request.attendance;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record CheckOutRequest(
    @NotNull
    Long employeeId,

    @NotNull
    LocalDate workDate
) {
}
