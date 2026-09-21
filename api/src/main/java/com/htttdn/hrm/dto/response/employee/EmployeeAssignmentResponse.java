package com.htttdn.hrm.dto.response.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentType;

public record EmployeeAssignmentResponse(
    Long id,
    Long employeeId,
    Long organizationUnitId,
    Long workLocationId,
    Long positionId,
    Long shiftId,
    Long managerEmployeeId,
    EmploymentType employmentType,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Boolean isPrimary
) {
}
