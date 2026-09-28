package com.htttdn.hrm.dto.response.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentType;

public record EmployeeAssignmentResponse(
    Long id,
    Long employeeId,
    Long organizationUnitId,
    String organizationUnitName,
    Long workLocationId,
    String workLocationName,
    Long positionId,
    String positionTitle,
    Long shiftId,
    String shiftName,
    Long managerEmployeeId,
    String managerEmployeeName,
    EmploymentType employmentType,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Boolean isPrimary
) {
}
