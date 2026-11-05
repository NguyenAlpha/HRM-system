package com.htttdn.hrm.dto.request.employee;

import com.htttdn.hrm.dto.request.ValidationPatterns;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeSensitiveRequest(
    @Size(max = 15)
    @Nullable
    String nationalId,

    @Email
    @Size(max = 30)
    @Nullable
    String personalEmail,

    @Nullable
    String address,

    @Size(max = 20)
    @Nullable
    String taxCode,

    @Size(max = 50)
    @Nullable
    String bankName,

    @Size(max = 20)
    @Nullable
    String bankAccountNumber,

    @Size(max = 50)
    @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
    @Nullable
    String bankAccountHolder
) {
}
