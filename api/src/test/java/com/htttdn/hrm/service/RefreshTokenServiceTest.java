package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.RefreshToken;
import com.htttdn.hrm.exception.InvalidTokenException;
import com.htttdn.hrm.repository.RefreshTokenRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void createReturnsRawTokenButStoresOnlyItsHash() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, 30);
        Account account = Account.builder().id(7L).build();

        String rawToken = service.create(account);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertEquals(64, stored.getTokenHash().length());
        assertNotEquals(rawToken, stored.getTokenHash());
        assertEquals(account, stored.getAccount());
        assertTrue(stored.getExpiresAt().isAfter(Instant.now().plusSeconds(29 * 24 * 60 * 60L)));
    }

    @Test
    void rotateRevokesOldTokenAndIssuesANewOne() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, 30);
        Account account = Account.builder().id(7L).build();
        RefreshToken existing = RefreshToken.builder()
            .account(account)
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.revokeIfActive(anyString(), any())).thenReturn(1);

        var result = service.rotate("old-refresh-token");

        assertEquals(7L, result.accountId());
        assertNotEquals("old-refresh-token", result.refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void reusedTokenRevokesEverySessionForTheAccount() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, 30);
        Account account = Account.builder().id(7L).build();
        RefreshToken existing = RefreshToken.builder()
            .account(account)
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.revokeIfActive(anyString(), any())).thenReturn(0);

        InvalidTokenException exception = assertThrows(
            InvalidTokenException.class,
            () -> service.rotate("reused-refresh-token")
        );

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
        verify(refreshTokenRepository).revokeAllByAccountId(org.mockito.ArgumentMatchers.eq(7L), any());
    }
}
