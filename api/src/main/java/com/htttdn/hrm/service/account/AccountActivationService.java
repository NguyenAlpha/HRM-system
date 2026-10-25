package com.htttdn.hrm.service.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.CompleteAccountActivationRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountActivationToken;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.InvalidTokenException;
import com.htttdn.hrm.repository.AccountActivationTokenRepository;
import com.htttdn.hrm.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountActivationService {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_RAW_TOKEN_LENGTH = 200;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final AccountActivationTokenRepository activationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountActivationProperties properties;

    /**
     * Phát credential kích hoạt dùng một lần. Raw token chỉ được trả về cho lớp điều phối
     * để gửi tới người nhận; database chỉ lưu SHA-256 hash.
     */
    @Transactional
    public IssuedActivationToken issue(Long accountId, Account createdByAccount) {
        Account account = accountRepository.getRequiredForUpdate(accountId);
        if (account.getStatus() != AccountStatus.PENDING) {
            throw new ConflictException(
                ErrorCode.ACCOUNT_ACTIVATION_NOT_ALLOWED,
                "Only a pending account can receive an activation token"
            );
        }

        Instant now = Instant.now();
        activationTokenRepository.revokeActiveByAccountId(accountId, now);

        String rawToken = generateToken();
        Instant expiresAt = now.plus(properties.activationTokenExpirationHours(), ChronoUnit.HOURS);
        activationTokenRepository.save(AccountActivationToken.builder()
            .account(account)
            .tokenHash(hash(rawToken))
            .expiresAt(expiresAt)
            .createdByAccount(createdByAccount)
            .build());

        return new IssuedActivationToken(rawToken, expiresAt);
    }

    @Transactional
    public void revokeAll(Long accountId) {
        activationTokenRepository.revokeActiveByAccountId(accountId, Instant.now());
    }

    @Transactional
    public void activate(String rawToken, CompleteAccountActivationRequest request) {
        validateRawToken(rawToken);
        if (!request.password().equals(request.passwordConfirmation())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Password confirmation does not match",
                "passwordConfirmation"
            );
        }

        AccountActivationToken activationToken = activationTokenRepository
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(this::invalidToken);

        Instant now = Instant.now();
        if (activationToken.getUsedAt() != null || activationToken.getRevokedAt() != null) {
            throw invalidToken();
        }
        if (!activationToken.getExpiresAt().isAfter(now)) {
            throw new InvalidTokenException(
                ErrorCode.ACTIVATION_TOKEN_EXPIRED,
                "Account activation token has expired"
            );
        }

        Account account = activationToken.getAccount();
        if (account.getStatus() != AccountStatus.PENDING) {
            throw invalidToken();
        }

        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        activationToken.setUsedAt(now);
        activationTokenRepository.revokeOtherActiveByAccountId(account.getId(), activationToken.getId(), now);
    }

    private void validateRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_RAW_TOKEN_LENGTH) {
            throw invalidToken();
        }
    }

    private InvalidTokenException invalidToken() {
        return new InvalidTokenException(
            ErrorCode.ACTIVATION_TOKEN_INVALID,
            "Invalid account activation token"
        );
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record IssuedActivationToken(String rawToken, Instant expiresAt) {
    }
}
