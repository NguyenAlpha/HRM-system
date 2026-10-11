package com.htttdn.hrm.dto.request.payroll;

import java.math.BigDecimal;

import com.htttdn.hrm.entity.enums.PayrollAdjustmentType;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePayrollAdjustmentRequest(
    @NotNull
    Long employeeId,

    @NotNull
    PayrollAdjustmentType type,

    @NotBlank
    @Size(max = 250)
    String description,

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    BigDecimal amount
) {
}
