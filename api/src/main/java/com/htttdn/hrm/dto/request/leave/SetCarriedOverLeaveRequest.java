package com.htttdn.hrm.dto.request.leave;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import jakarta.annotation.Nullable;

public record SetCarriedOverLeaveRequest(
    @NotNull
    @PositiveOrZero
    Integer carriedOverMinutes,
    @Nullable String reason
) {
}
