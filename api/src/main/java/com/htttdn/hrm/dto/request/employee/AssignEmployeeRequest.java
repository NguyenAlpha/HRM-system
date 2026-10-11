package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentType;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record AssignEmployeeRequest(
    @NotNull
    Long organizationUnitId,

    @NotNull
    Long workLocationId,

    @NotNull
    Long positionId,

    @Nullable
    Long shiftId,

    @Nullable
    Long managerEmployeeId,

    @NotNull
    EmploymentType employmentType,

    @NotNull
    LocalDate effectiveFrom,

    @Nullable
    String reason
) {
}
