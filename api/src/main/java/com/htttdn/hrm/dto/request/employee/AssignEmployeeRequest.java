package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentType;

import jakarta.validation.constraints.NotNull;

public record AssignEmployeeRequest(
    @NotNull
    Long organizationUnitId,

    @NotNull
    Long workLocationId,

    @NotNull
    Long positionId,

    Long shiftId,

    Long managerEmployeeId,

    @NotNull
    EmploymentType employmentType,

    @NotNull
    LocalDate effectiveFrom,

    String reason,

    @NotNull
    Long createdByAccountId
) {
}
