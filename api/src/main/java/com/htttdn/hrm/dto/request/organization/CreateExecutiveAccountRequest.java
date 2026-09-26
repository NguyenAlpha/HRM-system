package com.htttdn.hrm.dto.request.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateExecutiveAccountRequest(
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(
        regexp = "^[A-Za-z0-9._-]+$",
        message = "username may contain only letters, numbers, dots, underscores, and hyphens"
    )
    String username
) {
}
