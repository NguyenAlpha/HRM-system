package com.htttdn.hrm.dto.request.payroll;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePayrollPeriodRequest(
    @NotNull
    Short year,

    @NotNull
    @Min(1)
    @Max(12)
    Short month
) {
}
