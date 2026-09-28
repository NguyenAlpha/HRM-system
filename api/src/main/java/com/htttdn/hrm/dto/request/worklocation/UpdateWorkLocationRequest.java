package com.htttdn.hrm.dto.request.worklocation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWorkLocationRequest(
    Long parentLocationId,

    @NotBlank
    @Size(max = 150)
    String name,

    @NotBlank
    String address,

    @Size(max = 20)
    String phone,

    @NotNull
    Boolean isActive
) {
}
