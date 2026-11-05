package com.htttdn.hrm.dto.request.employeerequest;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateResignationRequestRequest(
    @NotNull
    Long employeeId,

    @NotNull
    LocalDate requestedLastWorkingDate,

    @NotBlank
    @Size(max = 500)
    String reason
) {
}
