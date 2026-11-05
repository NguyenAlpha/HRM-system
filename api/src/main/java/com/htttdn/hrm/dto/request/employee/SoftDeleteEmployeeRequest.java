package com.htttdn.hrm.dto.request.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record SoftDeleteEmployeeRequest(
    @NotBlank
    @Size(max = 500)
    String deletionReason
) {
}
