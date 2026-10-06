package com.htttdn.hrm.dto.request.payroll;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePayrollPeriodRequest(
    @NotNull
    Short year,

    @NotNull
    @Min(1)
    @Max(12)
    Short month,

    LocalDate taxPaymentDate
) {
    public CreatePayrollPeriodRequest(Short year, Short month) {
        this(year, month, null);
    }
}
