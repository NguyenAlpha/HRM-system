package com.htttdn.hrm.dto.request.jobposition;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateJobPositionRequest(
    @NotBlank
    @Size(max = 150)
    String title,

    @Nullable
    String description,

    @NotNull
    Boolean isManagerial,

    @NotNull
    Boolean isActive
) {
}
