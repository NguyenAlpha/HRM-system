package com.htttdn.hrm.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
    @NotBlank
    @Size(max = 200)
    String refreshToken
) {
}
