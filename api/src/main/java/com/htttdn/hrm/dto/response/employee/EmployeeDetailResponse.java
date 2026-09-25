package com.htttdn.hrm.dto.response.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.Gender;

public record EmployeeDetailResponse(
    Long id,
    String employeeCode,
    String fullName,
    LocalDate dateOfBirth,
    Gender gender,
    EducationLevel highestEducationLevel,
    String major,
    String institution,
    Short graduationYear,
    String workEmail,
    String phone,
    LocalDate hireDate,
    EmploymentStatus employmentStatus,
    LocalDate terminationDate,
    EmployeeAssignmentResponse currentAssignment
) {
}
