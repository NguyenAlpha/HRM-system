package com.htttdn.hrm.dto.request.payroll;

import java.time.LocalDate;

public record PayrollActionRequest(LocalDate taxPaymentDate) {
    public PayrollActionRequest() {
        this(null);
    }
}
