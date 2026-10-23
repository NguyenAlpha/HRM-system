package com.htttdn.hrm.ratelimit;

/**
 * The outcome of one quota check, with what the {@code RateLimit-*} and {@code Retry-After}
 * headers report. Seconds are rounded up so a client never retries too early.
 */
public record RateLimitDecision(
    boolean allowed,
    long limit,
    long remaining,
    long resetSeconds,
    long retryAfterSeconds
) {

    static long seconds(long nanos) {
        return nanos <= 0 ? 0 : (nanos + 999_999_999L) / 1_000_000_000L;
    }
}
