package com.htttdn.hrm.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.exception.RateLimitExceededException;

import lombok.RequiredArgsConstructor;

/**
 * Wrong passwords per account, so guessing one account's password from many addresses is still
 * slowed down. Only a wrong password uses a try; once they run out, sign-in answers 429 until the
 * bucket refills. The account itself is never locked, so nobody can lock someone else out.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    private final RateLimitService rateLimitService;

    /**
     * {@code account:<id>} for an existing account, so its username and email share one bucket;
     * otherwise a hash of what was typed, which bounds the key size and still slows down guessing
     * accounts that do not exist.
     */
    public static String key(Long accountId, String login) {
        return accountId != null ? "account:" + accountId : "name:" + sha256(login.toLowerCase(Locale.ROOT));
    }

    /** Throws {@link RateLimitExceededException} when no try is left; does not use one. */
    public void check(String key) {
        if (!rateLimitService.enabled()) {
            return;
        }
        RateLimitDecision decision = rateLimitService.check(RateLimitPolicy.LOGIN_FAILURE, key);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision);
        }
    }

    public void recordFailure(String key) {
        if (rateLimitService.enabled()) {
            rateLimitService.penalize(RateLimitPolicy.LOGIN_FAILURE, key);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", exception);
        }
    }
}
