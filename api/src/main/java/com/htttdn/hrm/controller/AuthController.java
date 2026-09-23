package com.htttdn.hrm.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.auth.ChangeOwnPasswordRequest;
import com.htttdn.hrm.dto.request.auth.LoginRequest;
import com.htttdn.hrm.dto.request.auth.LogoutRequest;
import com.htttdn.hrm.dto.request.auth.RefreshTokenRequest;
import com.htttdn.hrm.dto.response.auth.AuthAccountResponse;
import com.htttdn.hrm.dto.response.auth.AuthResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResult<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResult<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResult.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(accountId(jwt), request.refreshToken());
        return ApiResult.ok();
    }

    @GetMapping("/me")
    public ApiResult<AuthAccountResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResult.ok(authService.me(accountId(jwt)));
    }

    @PostMapping("/change-password")
    public ApiResult<Void> changePassword(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ChangeOwnPasswordRequest request
    ) {
        authService.changePassword(accountId(jwt), request);
        return ApiResult.ok();
    }

    private Long accountId(Jwt jwt) {
        Object accountId = jwt.getClaim("accountId");
        if (accountId instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Authenticated token does not contain a valid accountId claim");
    }
}
