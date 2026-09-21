package com.htttdn.hrm.dto.request.payroll;

import jakarta.validation.constraints.NotNull;

public record PayrollActionRequest(
    @NotNull
    Long accountId
) {
}
