package com.htttdn.hrm.dto.response.payroll;

import java.math.BigDecimal;
import java.time.Instant;

import com.htttdn.hrm.entity.enums.PayrollAdjustmentType;

public record PayrollAdjustmentResponse(
    Long id,
    Long payrollPeriodId,
    Long employeeId,
    String employeeCode,
    String employeeName,
    PayrollAdjustmentType type,
    String description,
    BigDecimal amount,
    Instant createdAt
) {
}
