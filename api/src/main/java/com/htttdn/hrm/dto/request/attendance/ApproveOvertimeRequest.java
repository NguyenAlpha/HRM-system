package com.htttdn.hrm.dto.request.attendance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** The approver's overtime decision; the multiplier is not chosen, it follows the day (see OvertimeService). */
public record ApproveOvertimeRequest(
    @NotNull
    @PositiveOrZero
    Integer overtimeMinutes
) {
}
