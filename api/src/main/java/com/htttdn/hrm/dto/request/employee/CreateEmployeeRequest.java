package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.Gender;

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
    String major,
    String institution,
    Short graduationYear,
    String nationalId,
    String personalEmail,
    String workEmail,
    String phone,
    String address,
    String taxCode,
    String bankName,
    String bankAccountNumber,
    String bankAccountHolder,

    @NotNull
    LocalDate hireDate
) {
}
