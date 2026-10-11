package com.htttdn.hrm.dto.response.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentStatus;

import jakarta.annotation.Nullable;

public record EmployeeSummaryResponse(
    Long id,
    String employeeCode,
    String fullName,
    @Nullable String workEmail,
    @Nullable String phone,
    LocalDate hireDate,
    EmploymentStatus employmentStatus,
    @Nullable LocalDate terminationDate,
    @Nullable EmployeeAssignmentResponse currentAssignment,
    @Nullable EmployeeAccountSummaryResponse account
) {
}
