package com.htttdn.hrm.dto.request.attendance;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import jakarta.annotation.Nullable;

public record ApproveOvertimeRequest(
    @NotNull
    @PositiveOrZero
    Integer overtimeMinutes,

    @NotNull
    @Positive
    BigDecimal overtimeMultiplier,

    @Nullable Boolean taxExempt
) {
    public ApproveOvertimeRequest(Integer overtimeMinutes, BigDecimal overtimeMultiplier) {
        this(overtimeMinutes, overtimeMultiplier, false);
    }
}
