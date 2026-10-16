package com.htttdn.hrm.dto.request.worklocation;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWorkLocationRequest(
    @Nullable
    Long parentLocationId,

    @NotBlank
    @Size(max = 30)
    String name,

    @NotBlank
    String address,

    @Size(max = 12)
    @Nullable
    String phone,

    @NotNull
    Boolean isActive
) {
}
