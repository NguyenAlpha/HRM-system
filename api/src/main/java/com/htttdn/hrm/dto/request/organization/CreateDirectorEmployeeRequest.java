package com.htttdn.hrm.dto.request.organization;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDirectorEmployeeRequest(
    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_-]+$")
    String employeeCode,

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
