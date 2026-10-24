package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.dto.request.ValidationPatterns;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.Gender;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEmployeeRequest(
    @NotBlank
    @Size(max = 40)
    String fullName,

    @Nullable
    LocalDate dateOfBirth,
    @Nullable
    Gender gender,
    @Nullable
    EducationLevel highestEducationLevel,

    @Size(max = 30)
    @Nullable
    String major,

    @Size(max = 30)
    @Nullable
    String institution,

    @Nullable
    Short graduationYear,

    @Email
    @Size(max = 30)
    @Nullable
    String workEmail,

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)

    @Size(max = 12)
    @Nullable
    String phone
) {
}
