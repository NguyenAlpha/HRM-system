package com.htttdn.hrm.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.RefreshToken;
import com.htttdn.hrm.exception.InvalidTokenException;
import com.htttdn.hrm.repository.RefreshTokenRepository;
import com.htttdn.hrm.security.JwtProperties;
import com.htttdn.hrm.util.Tokens;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwt;
    private final Clock clock;

    @Transactional
    public String create(Account account) {
        String rawToken = Tokens.random(TOKEN_BYTES);
        Instant now = Instant.now(clock);
        refreshTokenRepository.save(RefreshToken.builder()
            .tokenHash(Tokens.sha256Hex(rawToken))
            .account(account)
            .expiresAt(now.plus(jwt.refreshTokenExpirationDays(), ChronoUnit.DAYS))
            .build());
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public RotateResult rotate(String rawToken) {
        String tokenHash = Tokens.sha256Hex(rawToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidTokenException(
                ErrorCode.REFRESH_TOKEN_INVALID,
                "Invalid refresh token"
            ));

        Instant now = Instant.now(clock);
        int revoked = refreshTokenRepository.revokeIfActive(tokenHash, now);
        if (revoked == 0) {
            refreshTokenRepository.revokeAllByAccountId(existing.getAccount().getId(), now);
            throw new InvalidTokenException(
                ErrorCode.REFRESH_TOKEN_INVALID,
                "Refresh token has already been used"
            );
        }
        if (!existing.getExpiresAt().isAfter(now)) {
            throw new InvalidTokenException(
                ErrorCode.REFRESH_TOKEN_EXPIRED,
                "Refresh token has expired"
            );
        }

        Account account = existing.getAccount();
        String newToken = create(account);
        return new RotateResult(newToken, account.getId());
    }

    @Transactional
    public void revoke(String rawToken, Long accountId) {
        refreshTokenRepository.revokeForAccount(Tokens.sha256Hex(rawToken), accountId, Instant.now(clock));
    }

    @Transactional
    public void revokeAll(Long accountId) {
        refreshTokenRepository.revokeAllByAccountId(accountId, Instant.now(clock));
    }

    public record RotateResult(String refreshToken, Long accountId) {
    }
}
