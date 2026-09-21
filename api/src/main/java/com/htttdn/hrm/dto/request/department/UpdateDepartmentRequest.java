package com.htttdn.hrm.dto.request.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDepartmentRequest(
    @NotBlank
    String name,

    @NotNull
    Boolean isActive
) {
}
