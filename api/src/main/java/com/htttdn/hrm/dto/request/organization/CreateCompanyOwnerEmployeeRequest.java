package com.htttdn.hrm.dto.request.organization;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCompanyOwnerEmployeeRequest(
    @NotBlank
    @Size(max = 200)
    String fullName,

    @NotBlank
    @Email
    @Size(max = 100)
    String workEmail,

    @Size(max = 20)
    String phone,

    @NotNull
    LocalDate hireDate
) {
}
