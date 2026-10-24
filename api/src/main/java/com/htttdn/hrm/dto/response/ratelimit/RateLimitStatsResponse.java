package com.htttdn.hrm.dto.response.ratelimit;

import java.time.Instant;
import java.util.List;

/**
 * Rate-limit counters since {@code startedAt}, the last backend start; they are kept in memory
 * only. {@code topBlocked} lists the keys refused most often: an IP, a username, or for
 * {@code login-failure} {@code account:<id>} / {@code name:<sha256>}.
 */
public record RateLimitStatsResponse(
    Instant startedAt,
    boolean enabled,
    List<PolicyStats> policies,
    List<BlockedKeyStats> topBlocked
) {

    /**
     * {@code scope} is IP, USER or ACCOUNT: what a bucket of this policy is counted by.
     * {@code maxRequests}/{@code windowSeconds} are in force; {@code default*} come from
     * application.properties and apply again when an administrator's change ({@code customized})
     * is reset. {@code canDisable} is false only for {@code login-failure}.
     */
    public record PolicyStats(
        String policy,
        String scope,
        boolean enabled,
        boolean canDisable,
        boolean customized,
        long maxRequests,
        long windowSeconds,
        long defaultMaxRequests,
        long defaultWindowSeconds,
        long allowed,
        long blocked
    ) {
    }

    public record BlockedKeyStats(String policy, String scope, String key, long blocked) {
    }
}
