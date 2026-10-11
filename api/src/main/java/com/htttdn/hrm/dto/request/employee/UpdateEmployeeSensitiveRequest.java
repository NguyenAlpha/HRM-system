package com.htttdn.hrm.dto.request.employee;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeSensitiveRequest(
    @Size(max = 30)
    @Nullable
    String nationalId,

    @Email
    @Size(max = 100)
    @Nullable
    String personalEmail,

    @Nullable
    String address,

    @Size(max = 30)
    @Nullable
    String taxCode,

    @Size(max = 150)
    @Nullable
    String bankName,

    @Size(max = 50)
    @Nullable
    String bankAccountNumber,

    @Size(max = 200)
    @Nullable
    String bankAccountHolder
) {
}
