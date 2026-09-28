package com.htttdn.hrm.dto.request.jobposition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateJobPositionRequest(
    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z][A-Z0-9_-]*$")
    String code,

    @NotBlank
    @Size(max = 150)
    String title,

    String description,

    @NotNull
    Boolean isManagerial
) {
}
