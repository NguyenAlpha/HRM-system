package com.htttdn.hrm.dto.response.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.Gender;

import jakarta.annotation.Nullable;

public record EmployeeDetailResponse(
    Long id,
    String employeeCode,
    String fullName,
    @Nullable LocalDate dateOfBirth,
    @Nullable Gender gender,
    @Nullable EducationLevel highestEducationLevel,
    @Nullable String major,
    @Nullable String institution,
    @Nullable Short graduationYear,
    @Nullable String workEmail,
    @Nullable String phone,
    LocalDate hireDate,
    EmploymentStatus employmentStatus,
    @Nullable LocalDate terminationDate,
    /** Set while the employment end waits for {@code terminationDate} to pass: the status the employee then takes. */
    @Nullable EmploymentStatus scheduledEndStatus,
    @Nullable EmployeeAssignmentResponse currentAssignment,
    @Nullable EmployeeAccountSummaryResponse account
) {
}
