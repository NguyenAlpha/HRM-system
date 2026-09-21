package com.htttdn.hrm.dto.response.payroll;

import java.math.BigDecimal;

import com.htttdn.hrm.entity.enums.PayslipItemType;

public record PayslipItemResponse(
    Long id,
    PayslipItemType componentType,
    String description,
    BigDecimal quantity,
    BigDecimal unitRate,
    BigDecimal multiplier,
    BigDecimal amount
) {
}
