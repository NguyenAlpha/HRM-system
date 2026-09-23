package com.htttdn.hrm.dto.response.auth;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    AuthAccountResponse account
) {
}
