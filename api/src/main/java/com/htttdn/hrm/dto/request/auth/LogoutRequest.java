package com.htttdn.hrm.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
    @NotBlank
    @Size(max = 200)
    String refreshToken
) {
}
