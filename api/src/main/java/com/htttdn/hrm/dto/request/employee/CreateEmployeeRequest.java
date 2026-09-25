package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
    @NotBlank
    @Size(max = 30)
    String employeeCode,

    @NotBlank
    @Size(max = 200)
    String fullName,

    LocalDate dateOfBirth,
    Gender gender,
    EducationLevel highestEducationLevel,

    @Size(max = 200)
    String major,

    @Size(max = 200)
    String institution,

    Short graduationYear,

    @Size(max = 30)
    String nationalId,

    @Email
    @Size(max = 100)
    String personalEmail,

    @Email
    @Size(max = 100)
    String workEmail,

    @Size(max = 20)
    String phone,

    String address,

    @Size(max = 30)
    String taxCode,

    @Size(max = 150)
    String bankName,

    @Size(max = 50)
    String bankAccountNumber,

    @Size(max = 200)
    String bankAccountHolder,

    @NotNull
    LocalDate hireDate
) {
}
