package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.Gender;

import jakarta.validation.constraints.NotBlank;

public record UpdateEmployeeProfileRequest(
    @NotBlank
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
    String bankAccountHolder
) {
}
