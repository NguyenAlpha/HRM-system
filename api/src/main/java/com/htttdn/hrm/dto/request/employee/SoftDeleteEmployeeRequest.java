package com.htttdn.hrm.dto.request.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SoftDeleteEmployeeRequest(
    @NotNull
    Long deletedByAccountId,

    @NotBlank
    String deletionReason
) {
}
