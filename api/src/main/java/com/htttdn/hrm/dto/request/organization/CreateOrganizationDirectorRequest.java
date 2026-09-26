package com.htttdn.hrm.dto.request.organization;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganizationDirectorRequest(
    @NotNull
    @Valid
    CreateDirectorEmployeeRequest employee,

    @NotNull
    @Valid
    CreateDirectorAccountRequest account,

    @NotNull
    LocalDate effectiveFrom,

    @NotBlank
    @Size(max = 500)
    String appointmentReason
) {
}
