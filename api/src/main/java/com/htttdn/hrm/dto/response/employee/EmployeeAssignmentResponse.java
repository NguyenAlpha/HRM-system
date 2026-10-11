package com.htttdn.hrm.dto.response.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentType;

import jakarta.annotation.Nullable;

public record EmployeeAssignmentResponse(
    Long id,
    Long employeeId,
    Long organizationUnitId,
    String organizationUnitName,
    Long workLocationId,
    String workLocationName,
    Long positionId,
    String positionTitle,
    @Nullable Long shiftId,
    @Nullable String shiftName,
    @Nullable Long managerEmployeeId,
    @Nullable String managerEmployeeName,
    EmploymentType employmentType,
    LocalDate effectiveFrom,
    @Nullable LocalDate effectiveTo,
    Boolean isPrimary
) {
}
