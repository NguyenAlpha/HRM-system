package com.htttdn.hrm.dto.request.attendance;

import jakarta.validation.constraints.NotNull;

public record CheckInRequest(
    @NotNull
    Long employeeId
) {
}
