package com.htttdn.hrm.dto.request.employee;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EmploymentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EndEmploymentRequest(
    /** RESIGNED, TERMINATED or RETIRED; the service rejects any other status. */
    @NotNull
    EmploymentStatus endStatus,
    @NotNull
    LocalDate terminationDate,
    @NotBlank
    @Size(max = 500)
    String terminationReason
) {
}
