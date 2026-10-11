package com.htttdn.hrm.dto.response.employee;

import jakarta.annotation.Nullable;

public record EmployeeSensitiveResponse(
    Long employeeId,
    @Nullable String nationalId,
    @Nullable String personalEmail,
    @Nullable String address,
    @Nullable String taxCode,
    @Nullable String bankName,
    @Nullable String bankAccountNumber,
    @Nullable String bankAccountHolder
) {
}
