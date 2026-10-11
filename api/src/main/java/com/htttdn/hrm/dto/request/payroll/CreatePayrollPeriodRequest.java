package com.htttdn.hrm.dto.request.payroll;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import jakarta.annotation.Nullable;

public record CreatePayrollPeriodRequest(
    @NotNull
    Short year,

    @NotNull
    @Min(1)
    @Max(12)
    Short month,

    @Nullable LocalDate taxPaymentDate
) {
    public CreatePayrollPeriodRequest(Short year, Short month) {
        this(year, month, null);
    }
}
