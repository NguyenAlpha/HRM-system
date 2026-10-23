package com.htttdn.hrm.ratelimit;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;

/**
 * Token buckets kept in memory, one per policy and key (IP, username or account), plus the
 * allowed/blocked counters shown to administrators. Everything is lost on restart and is not
 * shared between backend instances; see docs/concepts/RATE_LIMIT.md.
 */
@Service
public class RateLimitService {

    private static final int MAX_BUCKETS_PER_POLICY = 10_000;
    private static final int MAX_TRACKED_BLOCKED_KEYS = 1_000;

    private final RateLimitProperties properties;
    private final Map<RateLimitPolicy, Cache<String, Bucket>> buckets = new EnumMap<>(RateLimitPolicy.class);
    private final Map<RateLimitPolicy, LongAdder> allowed = new EnumMap<>(RateLimitPolicy.class);
    private final Map<RateLimitPolicy, LongAdder> blocked = new EnumMap<>(RateLimitPolicy.class);
    private final Cache<BlockedKey, LongAdder> blockedByKey = Caffeine.newBuilder()
        .maximumSize(MAX_TRACKED_BLOCKED_KEYS)
        .build();

    public RateLimitService(RateLimitProperties properties) {
        this.properties = properties;
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            // A bucket left alone for a whole window is full again, so dropping it loses nothing.
            buckets.put(policy, Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS_PER_POLICY)
                .expireAfterAccess(properties.quota(policy).window())
                .build());
            allowed.put(policy, new LongAdder());
            blocked.put(policy, new LongAdder());
        }
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** Takes one token, or reports when the next one is available. */
    public RateLimitDecision tryConsume(RateLimitPolicy policy, String key) {
        ConsumptionProbe probe = bucket(policy, key).tryConsumeAndReturnRemaining(1);
        record(policy, key, probe.isConsumed());
        return new RateLimitDecision(
            probe.isConsumed(),
            properties.quota(policy).maxRequests(),
            probe.getRemainingTokens(),
            RateLimitDecision.seconds(probe.getNanosToWaitForReset()),
            RateLimitDecision.seconds(probe.getNanosToWaitForRefill())
        );
    }

    /** Whether a token is left, without taking it; {@link #penalize} takes it later. */
    public RateLimitDecision check(RateLimitPolicy policy, String key) {
        EstimationProbe probe = bucket(policy, key).estimateAbilityToConsume(1);
        record(policy, key, probe.canBeConsumed());
        long retryAfter = RateLimitDecision.seconds(probe.getNanosToWaitForRefill());
        return new RateLimitDecision(
            probe.canBeConsumed(),
            properties.quota(policy).maxRequests(),
            probe.getRemainingTokens(),
            retryAfter,
            retryAfter
        );
    }

    /** Takes one token without counting a request, for failures counted after {@link #check}. */
    public void penalize(RateLimitPolicy policy, String key) {
        bucket(policy, key).tryConsume(1);
    }

    long allowedCount(RateLimitPolicy policy) {
        return allowed.get(policy).sum();
    }

    long blockedCount(RateLimitPolicy policy) {
        return blocked.get(policy).sum();
    }

    private Bucket bucket(RateLimitPolicy policy, String key) {
        RateLimitProperties.Quota quota = properties.quota(policy);
        return buckets.get(policy).get(key, ignored -> Bucket.builder()
            .addLimit(limit -> limit.capacity(quota.maxRequests())
                .refillGreedy(quota.maxRequests(), quota.window()))
            .build());
    }

    private void record(RateLimitPolicy policy, String key, boolean wasAllowed) {
        if (wasAllowed) {
            allowed.get(policy).increment();
            return;
        }
        blocked.get(policy).increment();
        blockedByKey.get(new BlockedKey(policy, key), ignored -> new LongAdder()).increment();
    }

    record BlockedKey(RateLimitPolicy policy, String key) {
    }
}
