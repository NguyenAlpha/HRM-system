package com.htttdn.hrm.dto.response.payroll;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;

public record PayrollPeriodResponse(
    Long id,
    Short year,
    Short month,
    LocalDate periodStart,
    LocalDate periodEnd,
    PayrollPeriodStatus status,
    Long calculatedByAccountId,
    Instant calculatedAt,
    Long approvedByAccountId,
    Instant approvedAt,
    Long paidByAccountId,
    Instant paidAt,
    Long lockedByAccountId,
    Instant lockedAt
) {
}
