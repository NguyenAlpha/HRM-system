package com.htttdn.hrm.dto.request.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeSensitiveRequest(
    @Size(max = 30)
    String nationalId,

    @Email
    @Size(max = 100)
    String personalEmail,

    String address,

    @Size(max = 30)
    String taxCode,

    @Size(max = 150)
    String bankName,

    @Size(max = 50)
    String bankAccountNumber,

    @Size(max = 200)
    String bankAccountHolder
) {
}
