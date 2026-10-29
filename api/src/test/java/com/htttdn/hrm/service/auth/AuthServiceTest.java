package com.htttdn.hrm.service.auth;

import com.htttdn.hrm.config.ClockConfig;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.htttdn.hrm.service.rbac.AccountAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.htttdn.hrm.dto.request.auth.ChangeOwnPasswordRequest;
import com.htttdn.hrm.dto.request.auth.LoginRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.InvalidTokenException;
import com.htttdn.hrm.exception.UnauthorizedException;
import com.htttdn.hrm.ratelimit.LoginAttemptLimiter;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationSnapshot;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationPermission;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccountAuthorizationService accountAuthorizationService;

    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    @Test
    void loginReturnsTokenBundleAndResetsFailedAttempts() {
        Account account = activeAccount();
        account.setFailedLoginCount(2);
        when(accountRepository.findBySignInName("admin")).thenReturn(Optional.of(account));
        when(refreshTokenService.create(account)).thenReturn("refresh-token");
        when(accountAuthorizationService.getSnapshot(1L)).thenReturn(
            new AuthorizationSnapshot(
                List.of("SYSTEM_ADMIN"),
                List.of("organization.company_owner.bootstrap"),
                List.of(new AuthorizationRole(
                    "SYSTEM_ADMIN", "Quản trị viên hệ thống", RoleScopeType.COMPANY,
                    null, null, null, null
                )),
                List.of(new AuthorizationPermission(
                    "organization.company_owner.bootstrap", "Khởi tạo Chủ sở hữu doanh nghiệp",
                    PermissionModule.ORGANIZATION
                ))
            )
        );
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        var response = service().login(new LoginRequest("admin", "Admin@123"));

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(0, account.getFailedLoginCount());
        assertNotNull(account.getLastLoginAt());
        assertEquals("SYSTEM_ADMIN", response.account().roles().getFirst().code());
        assertEquals("Quản trị viên hệ thống", response.account().roles().getFirst().name());
        assertEquals("Khởi tạo Chủ sở hữu doanh nghiệp", response.account().permissions().getFirst().name());
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void invalidPasswordIncrementsCounterWithoutLockingAccount() {
        Account account = activeAccount();
        account.setFailedLoginCount(4);
        when(accountRepository.findBySignInName("admin")).thenReturn(Optional.of(account));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        UnauthorizedException exception = assertThrows(
            UnauthorizedException.class,
            () -> service().login(new LoginRequest("admin", "wrong-password"))
        );

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(5, account.getFailedLoginCount());
    }

    @Test
    void aWrongPasswordDoesNotTellWhetherTheAccountIsDisabled() {
        Account account = activeAccount();
        account.setStatus(AccountStatus.DISABLED);
        when(accountRepository.findBySignInName("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "password-hash")).thenReturn(false);

        UnauthorizedException exception = assertThrows(
            UnauthorizedException.class,
            () -> service().login(new LoginRequest("admin", "wrong-password"))
        );

        // Same answer as for any wrong password, so a guessed username reveals nothing about the account.
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verify(loginAttemptLimiter).recordFailure(any());
    }

    @Test
    void theRightPasswordLearnsThatTheAccountIsDisabled() {
        Account account = activeAccount();
        account.setStatus(AccountStatus.DISABLED);
        when(accountRepository.findBySignInName("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Admin@123", "password-hash")).thenReturn(true);

        UnauthorizedException exception = assertThrows(
            UnauthorizedException.class,
            () -> service().login(new LoginRequest("admin", "Admin@123"))
        );

        assertEquals(ErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
    }

    @Test
    void refreshRevokesRotatedTokenWhenAccountIsDisabled() {
        Account account = activeAccount();
        account.setStatus(AccountStatus.DISABLED);
        when(refreshTokenService.rotate("refresh-token"))
            .thenReturn(new RefreshTokenService.RotateResult("new-token", 1L));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        InvalidTokenException exception = assertThrows(
            InvalidTokenException.class,
            () -> service().refresh("refresh-token")
        );

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
        verify(refreshTokenService).revokeAll(1L);
    }

    @Test
    void meRejectsTokenWhoseAccountNoLongerExists() {
        when(accountRepository.findById(4L)).thenReturn(Optional.empty());

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> service().me(4L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void changePasswordRevokesAllRefreshTokens() {
        Account account = activeAccount();
        account.setPasswordHash("old-hash");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service().changePassword(1L, new ChangeOwnPasswordRequest("old-password", "new-password"));

        assertEquals("new-hash", account.getPasswordHash());
        verify(refreshTokenService).revokeAll(1L);
    }

    private AuthService service() {
        return new AuthService(
            accountRepository,
            authenticationManager,
            passwordEncoder,
            jwtService,
            refreshTokenService,
            accountAuthorizationService,
            loginAttemptLimiter,
            Clock.system(ClockConfig.BUSINESS_ZONE)
        );
    }

    private Account activeAccount() {
        return Account.builder()
            .id(1L)
            .username("admin")
            .email("admin@hrm.local")
            .passwordHash("password-hash")
            .status(AccountStatus.ACTIVE)
            .failedLoginCount(0)
            .build();
    }
}
