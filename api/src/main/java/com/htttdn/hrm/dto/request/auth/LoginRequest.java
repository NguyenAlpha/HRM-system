package com.htttdn.hrm.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank
    @Size(max = 100)
    String usernameOrEmail,

    @NotBlank
    @Size(max = 100)
    String password
) {
}
