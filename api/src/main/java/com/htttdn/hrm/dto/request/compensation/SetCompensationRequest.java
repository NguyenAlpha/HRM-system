package com.htttdn.hrm.dto.request.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.CompensationType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SetCompensationRequest(
    @NotNull
    CompensationType componentType,

    @NotBlank
    String componentCode,

    @NotBlank
    String componentName,

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    BigDecimal monthlyAmount,

    @NotNull
    LocalDate effectiveFrom,

    String note
) {
}
