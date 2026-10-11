package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.Gender;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmployeeProfileRequest(
    @NotBlank
    @Size(max = 200)
    String fullName,

    @NotNull
    LocalDate dateOfBirth,
    @Nullable
    Gender gender,
    @Nullable
    EducationLevel highestEducationLevel,

    @Size(max = 200)
    @Nullable
    String major,

    @Size(max = 200)
    @Nullable
    String institution,

    @Nullable
    Short graduationYear,

    @Email
    @Size(max = 100)
    @Nullable
    String workEmail,

    @Size(max = 20)
    @Nullable
    String phone,

    @NotNull
    LocalDate hireDate
) {
}
