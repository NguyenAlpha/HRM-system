package com.htttdn.hrm.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code rate-limit.*} from application.properties. Every {@link RateLimitPolicy} must have a
 * positive {@code max-requests} and {@code window-seconds}, otherwise startup fails.
 */
@ConfigurationProperties("rate-limit")
public record RateLimitProperties(
    boolean enabled,
    List<String> trustedProxies,
    Map<String, Quota> policies
) {

    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        policies = policies == null ? Map.of() : Map.copyOf(policies);
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            Quota quota = policies.get(policy.key());
            if (quota == null || quota.maxRequests() <= 0 || quota.windowSeconds() <= 0) {
                throw new IllegalStateException("rate-limit.policies." + policy.key()
                    + " needs a positive max-requests and window-seconds");
            }
        }
    }

    public Quota quota(RateLimitPolicy policy) {
        return policies.get(policy.key());
    }

    public record Quota(long maxRequests, long windowSeconds) {

        public Duration window() {
            return Duration.ofSeconds(windowSeconds);
        }
    }
}
