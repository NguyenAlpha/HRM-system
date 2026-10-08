package com.htttdn.hrm.dto.request.leave;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SetCarriedOverLeaveRequest(
    @NotNull
    @PositiveOrZero
    Integer carriedOverMinutes,
    String reason
) {
}
