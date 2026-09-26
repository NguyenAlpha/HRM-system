package com.htttdn.hrm.dto.request.organization;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganizationCompanyOwnerRequest(
    @NotNull
    @Valid
    CreateCompanyOwnerEmployeeRequest employee,

    @NotNull
    @Valid
    CreateCompanyOwnerAccountRequest account,

    @NotNull
    LocalDate effectiveFrom,

    @NotBlank
    @Size(max = 500)
    String ownershipReason,

    @NotBlank
    @Size(max = 500)
    String directorAppointmentReason
) {
}
