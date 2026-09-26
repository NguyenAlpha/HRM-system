package com.htttdn.hrm.dto.response.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentStatus;

public record EmployeeSummaryResponse(
    Long id,
    String employeeCode,
    String fullName,
    String workEmail,
    String phone,
    LocalDate hireDate,
    EmploymentStatus employmentStatus,
    LocalDate terminationDate,
    EmployeeAccountSummaryResponse account
) {
}
