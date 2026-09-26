package com.htttdn.hrm.dto.request.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateEmployeeRequest(
    @Valid
    @NotNull
    CreateEmployeeProfileRequest employee,

    @Valid
    @NotNull
    AssignEmployeeRequest initialAssignment
) {
}
