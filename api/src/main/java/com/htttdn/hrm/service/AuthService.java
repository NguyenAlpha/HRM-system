package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.auth.ChangeOwnPasswordRequest;
import com.htttdn.hrm.dto.request.auth.LoginRequest;
import com.htttdn.hrm.dto.response.auth.AuthAccountResponse;
import com.htttdn.hrm.dto.response.auth.AuthEmployeeResponse;
import com.htttdn.hrm.dto.response.auth.AuthPermissionResponse;
import com.htttdn.hrm.dto.response.auth.AuthResponse;
import com.htttdn.hrm.dto.response.auth.AuthRoleResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.InvalidTokenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.exception.UnauthorizedException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AccountAuthorizationService accountAuthorizationService;

    public AuthService(
        AccountRepository accountRepository,
        AuthenticationManager authenticationManager,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenService refreshTokenService,
        AccountAuthorizationService accountAuthorizationService
    ) {
        this.accountRepository = accountRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse login(LoginRequest request) {
        String login = normalizeLogin(request.usernameOrEmail());
        Account account = accountRepository.findByLogin(login).orElse(null);

        if (account != null) {
            ensureLoginAllowed(account);
        }

        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login, request.password())
            );
        } catch (AuthenticationException exception) {
            recordFailedAttempt(account);
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }

        if (account == null) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }

        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        account.setLastLoginAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return issueTokenBundle(account);
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotateResult rotated = refreshTokenService.rotate(rawRefreshToken);
        Account account = accountRepository.findById(rotated.accountId()).orElse(null);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            refreshTokenService.revokeAll(rotated.accountId());
            throw new InvalidTokenException(
                ErrorCode.REFRESH_TOKEN_INVALID,
                "Account cannot refresh this session"
            );
        }

        return buildAuthResponse(account, rotated.refreshToken());
    }

    @Transactional(readOnly = true)
    public AuthAccountResponse me(Long accountId) {
        Account account = findAccount(accountId);
        AuthorizationSnapshot authorization = accountAuthorizationService.getSnapshot(accountId);
        return toAccountResponse(account, authorization);
    }

    @Transactional
    public void logout(Long accountId, String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken, accountId);
    }

    @Transactional
    public void changePassword(Long accountId, ChangeOwnPasswordRequest request) {
        Account account = findAccount(accountId);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "New password must be different from the current password",
                "newPassword"
            );
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setUpdatedAt(Instant.now());
        refreshTokenService.revokeAll(accountId);
    }

    private AuthResponse issueTokenBundle(Account account) {
        return buildAuthResponse(account, refreshTokenService.create(account));
    }

    private AuthResponse buildAuthResponse(Account account, String refreshToken) {
        AuthorizationSnapshot authorization = accountAuthorizationService.getSnapshot(account.getId());
        String accessToken = jwtService.generateAccessToken(account, authorization);
        return new AuthResponse(
            accessToken,
            refreshToken,
            "Bearer",
            jwtService.getAccessTokenExpirationSeconds(),
            toAccountResponse(account, authorization)
        );
    }

    private AuthAccountResponse toAccountResponse(Account account, AuthorizationSnapshot authorization) {
        return new AuthAccountResponse(
            account.getId(),
            account.getUsername(),
            account.getEmail(),
            account.getStatus(),
            account.getEmployee() == null ? null : new AuthEmployeeResponse(
                account.getEmployee().getId(),
                account.getEmployee().getEmployeeCode(),
                account.getEmployee().getFullName()
            ),
            authorization.roleDetails().stream()
                .map(role -> new AuthRoleResponse(
                    role.code(),
                    role.name(),
                    role.scopeType(),
                    role.organizationUnitId(),
                    role.organizationUnitName(),
                    role.workLocationId(),
                    role.workLocationName()
                ))
                .toList(),
            authorization.permissionDetails().stream()
                .map(permission -> new AuthPermissionResponse(
                    permission.code(),
                    permission.name(),
                    permission.module()
                ))
                .toList()
        );
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private void ensureLoginAllowed(Account account) {
        switch (account.getStatus()) {
            case ACTIVE -> {
                return;
            }
            case PENDING -> throw new UnauthorizedException(
                ErrorCode.ACCOUNT_PENDING,
                "Account is pending activation"
            );
            case LOCKED -> throw new UnauthorizedException(
                ErrorCode.ACCOUNT_LOCKED,
                "Account is locked"
            );
            case DISABLED -> throw new UnauthorizedException(
                ErrorCode.ACCOUNT_DISABLED,
                "Account is disabled"
            );
        }
    }

    private void recordFailedAttempt(Account account) {
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            return;
        }

        int failedAttempts = account.getFailedLoginCount() == null
            ? 1
            : account.getFailedLoginCount() + 1;
        account.setFailedLoginCount(failedAttempts);
        account.setUpdatedAt(Instant.now());
    }

    private String normalizeLogin(String value) {
        String trimmed = value.trim();
        return trimmed.contains("@") ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }
}
