package com.htttdn.hrm.dto.request.organization;

import java.time.LocalDate;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCompanyOwnerEmployeeRequest(
    @NotBlank
    @Size(max = 30)
    String fullName,

    @NotBlank
    @Email
    @Size(max = 30)
    String workEmail,

    @Size(max = 12)
    @Nullable
    String phone,

    @NotNull
    LocalDate hireDate
) {
}
