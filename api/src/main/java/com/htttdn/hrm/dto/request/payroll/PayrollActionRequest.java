package com.htttdn.hrm.dto.request.payroll;

import java.time.LocalDate;

import jakarta.annotation.Nullable;

public record PayrollActionRequest(@Nullable LocalDate taxPaymentDate) {
    public PayrollActionRequest() {
        this(null);
    }
}
