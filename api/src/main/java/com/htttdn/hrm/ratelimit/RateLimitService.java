package com.htttdn.hrm.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitStatsResponse;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitStatsResponse.BlockedKeyStats;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitStatsResponse.PolicyStats;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitTimelineResponse;
import com.htttdn.hrm.timeline.TimelineRange;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;

/**
 * Token buckets kept in memory, one per policy and key (IP, username or account), plus the
 * allowed/blocked counters shown to administrators. Everything is lost on restart and is not
 * shared between backend instances; see docs/concepts/RATE_LIMIT.md.
 *
 * <p>Each policy starts with its application.properties quota; {@link #configure} replaces it at
 * runtime (RateLimitSettingsService stores those changes in the database and replays them on start).
 */
@Service
public class RateLimitService {

    private static final int MAX_BUCKETS_PER_POLICY = 10_000;
    private static final int MAX_TRACKED_BLOCKED_KEYS = 1_000;
    private static final int TOP_BLOCKED_KEYS = 20;

    private final RateLimitProperties properties;
    private final Map<RateLimitPolicy, PolicyState> states = new ConcurrentHashMap<>();
    private final Map<RateLimitPolicy, LongAdder> allowed = new EnumMap<>(RateLimitPolicy.class);
    private final Map<RateLimitPolicy, LongAdder> blocked = new EnumMap<>(RateLimitPolicy.class);
    private final Cache<BlockedKey, LongAdder> blockedByKey = Caffeine.newBuilder()
        .maximumSize(MAX_TRACKED_BLOCKED_KEYS)
        .build();
    private final RateLimitTimeline timeline = new RateLimitTimeline();
    private final Clock clock;
    private final Instant startedAt;

    public RateLimitService(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.startedAt = clock.instant();
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            states.put(policy, PolicyState.of(properties.quota(policy), true, false));
            allowed.put(policy, new LongAdder());
            blocked.put(policy, new LongAdder());
        }
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** Whether requests of this policy are limited: rate limiting is on and the policy is not switched off. */
    public boolean enabled(RateLimitPolicy policy) {
        return properties.enabled() && states.get(policy).enabled();
    }

    /**
     * Replaces the policy's quota and on/off state. Its buckets start over, so every key gets the
     * new quota at once, with a full bucket.
     */
    public void configure(RateLimitPolicy policy, RateLimitProperties.Quota quota, boolean enabled) {
        if (!enabled && !policy.canDisable()) {
            throw new IllegalArgumentException(policy.key() + " cannot be disabled");
        }
        states.put(policy, PolicyState.of(quota, enabled, true));
    }

    /** Back to the application.properties quota, switched on; its buckets start over. */
    public void restoreDefault(RateLimitPolicy policy) {
        states.put(policy, PolicyState.of(properties.quota(policy), true, false));
    }

    /** Takes one token, or reports when the next one is available. */
    public RateLimitDecision tryConsume(RateLimitPolicy policy, String key) {
        PolicyState state = states.get(policy);
        ConsumptionProbe probe = state.bucket(key).tryConsumeAndReturnRemaining(1);
        record(policy, key, probe.isConsumed());
        return new RateLimitDecision(
            probe.isConsumed(),
            state.quota().maxRequests(),
            probe.getRemainingTokens(),
            RateLimitDecision.seconds(probe.getNanosToWaitForReset()),
            RateLimitDecision.seconds(probe.getNanosToWaitForRefill())
        );
    }

    /** Whether a token is left, without taking it; {@link #penalize} takes it later. */
    public RateLimitDecision check(RateLimitPolicy policy, String key) {
        PolicyState state = states.get(policy);
        EstimationProbe probe = state.bucket(key).estimateAbilityToConsume(1);
        record(policy, key, probe.canBeConsumed());
        long retryAfter = RateLimitDecision.seconds(probe.getNanosToWaitForRefill());
        return new RateLimitDecision(
            probe.canBeConsumed(),
            state.quota().maxRequests(),
            probe.getRemainingTokens(),
            retryAfter,
            retryAfter
        );
    }

    /** Takes one token without counting a request, for failures counted after {@link #check}. */
    public void penalize(RateLimitPolicy policy, String key) {
        states.get(policy).bucket(key).tryConsume(1);
    }

    /** Every policy with its quota and counters, and the keys blocked most often. */
    public RateLimitStatsResponse stats() {
        List<PolicyStats> policies = Arrays.stream(RateLimitPolicy.values())
            .map(policy -> {
                PolicyState state = states.get(policy);
                return new PolicyStats(
                    policy.key(),
                    policy.scope().name(),
                    state.enabled(),
                    policy.canDisable(),
                    state.customized(),
                    state.quota().maxRequests(),
                    state.quota().windowSeconds(),
                    properties.quota(policy).maxRequests(),
                    properties.quota(policy).windowSeconds(),
                    allowedCount(policy),
                    blockedCount(policy)
                );
            })
            .toList();
        List<BlockedKeyStats> topBlocked = blockedByKey.asMap().entrySet().stream()
            .map(entry -> new BlockedKeyStats(
                entry.getKey().policy().key(),
                entry.getKey().policy().scope().name(),
                entry.getKey().key(),
                entry.getValue().sum()
            ))
            .sorted(Comparator.comparingLong(BlockedKeyStats::blocked).reversed())
            .limit(TOP_BLOCKED_KEYS)
            .toList();
        return new RateLimitStatsResponse(startedAt, properties.enabled(), policies, topBlocked);
    }

    /** Requests and blocks over the range, for one policy or, when it is null, for all of them. */
    public RateLimitTimelineResponse timeline(TimelineRange range, RateLimitPolicy policy) {
        return new RateLimitTimelineResponse(
            range.name(),
            policy == null ? null : policy.key(),
            range.slotSeconds(),
            startedAt,
            timeline.points(range, policy, clock.instant())
        );
    }

    long allowedCount(RateLimitPolicy policy) {
        return allowed.get(policy).sum();
    }

    long blockedCount(RateLimitPolicy policy) {
        return blocked.get(policy).sum();
    }

    private void record(RateLimitPolicy policy, String key, boolean wasAllowed) {
        timeline.record(policy, !wasAllowed, clock.instant());
        if (wasAllowed) {
            allowed.get(policy).increment();
            return;
        }
        blocked.get(policy).increment();
        blockedByKey.get(new BlockedKey(policy, key), ignored -> new LongAdder()).increment();
    }

    record BlockedKey(RateLimitPolicy policy, String key) {
    }

    /** A policy's quota in force with its own buckets, replaced as a whole when an administrator changes it. */
    private record PolicyState(
        RateLimitProperties.Quota quota,
        boolean enabled,
        boolean customized,
        Cache<String, Bucket> buckets
    ) {

        static PolicyState of(RateLimitProperties.Quota quota, boolean enabled, boolean customized) {
            // A bucket left alone for a whole window is full again, so dropping it loses nothing.
            return new PolicyState(quota, enabled, customized, Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS_PER_POLICY)
                .expireAfterAccess(quota.window())
                .build());
        }

        Bucket bucket(String key) {
            return buckets.get(key, ignored -> Bucket.builder()
                .addLimit(limit -> limit.capacity(quota.maxRequests())
                    .refillGreedy(quota.maxRequests(), quota.window()))
                .build());
        }
    }
}
