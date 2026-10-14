package com.htttdn.hrm.dto.response.payroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;

import jakarta.annotation.Nullable;

public record PayrollPeriodResponse(
    Long id,
    Short year,
    Short month,
    LocalDate periodStart,
    LocalDate periodEnd,
    @Nullable LocalDate taxPaymentDate,
    PayrollPeriodStatus status,
    @Nullable Long calculatedByAccountId,
    @Nullable Instant calculatedAt,
    @Nullable Long approvedByAccountId,
    @Nullable Instant approvedAt,
    @Nullable Long paidByAccountId,
    @Nullable Instant paidAt,
    @Nullable Long lockedByAccountId,
    @Nullable Instant lockedAt,
    /** Payslips currently attached to the period; 0 until it is calculated. */
    long payslipCount,
    BigDecimal totalGrossPay,
    BigDecimal totalNetPay
) {
}
