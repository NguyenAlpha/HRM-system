package com.htttdn.hrm.dto.request.employee;

import jakarta.validation.constraints.NotBlank;
public record SoftDeleteEmployeeRequest(
    @NotBlank
    String deletionReason
) {
}
