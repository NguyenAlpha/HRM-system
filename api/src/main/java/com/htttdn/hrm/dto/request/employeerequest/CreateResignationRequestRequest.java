package com.htttdn.hrm.dto.request.employeerequest;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateResignationRequestRequest(
    @NotNull
    Long employeeId,

    @NotNull
    LocalDate requestedLastWorkingDate,

    @NotBlank
    String reason
) {
}
