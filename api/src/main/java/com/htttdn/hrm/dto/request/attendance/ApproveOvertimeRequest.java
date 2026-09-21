package com.htttdn.hrm.dto.request.attendance;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ApproveOvertimeRequest(
    @NotNull
    @PositiveOrZero
    Integer overtimeMinutes,

    @NotNull
    @Positive
    BigDecimal overtimeMultiplier,

    @NotNull
    Long approverAccountId
) {
}
