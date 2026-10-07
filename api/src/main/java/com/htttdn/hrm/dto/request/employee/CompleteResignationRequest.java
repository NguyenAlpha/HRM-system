package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompleteResignationRequest(
    @NotNull
    LocalDate terminationDate,
    @NotBlank
    String terminationReason
) {
}
