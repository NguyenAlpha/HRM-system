package com.htttdn.hrm.ratelimit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.htttdn.hrm.dto.response.ratelimit.RateLimitStatsResponse.PolicyStats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void blocksOnceTheQuotaIsUsedAndReportsWhenToRetry() {
        RateLimitService service = new RateLimitService(properties(2, 60));

        RateLimitDecision first = service.tryConsume(RateLimitPolicy.API, "alice");
        RateLimitDecision second = service.tryConsume(RateLimitPolicy.API, "alice");
        RateLimitDecision third = service.tryConsume(RateLimitPolicy.API, "alice");

        assertTrue(first.allowed());
        assertEquals(2, first.limit());
        assertEquals(1, first.remaining());
        assertTrue(second.allowed());
        assertEquals(0, second.remaining());
        assertFalse(third.allowed());
        assertTrue(third.retryAfterSeconds() > 0 && third.retryAfterSeconds() <= 30);
        assertEquals(2, service.allowedCount(RateLimitPolicy.API));
        assertEquals(1, service.blockedCount(RateLimitPolicy.API));
    }

    @Test
    void countsEveryKeyAndPolicySeparately() {
        RateLimitService service = new RateLimitService(properties(1, 60));

        assertTrue(service.tryConsume(RateLimitPolicy.API, "alice").allowed());
        assertTrue(service.tryConsume(RateLimitPolicy.API, "bob").allowed());
        assertTrue(service.tryConsume(RateLimitPolicy.EXPORT, "alice").allowed());
        assertFalse(service.tryConsume(RateLimitPolicy.API, "alice").allowed());
    }

    @Test
    void refillsAfterTheWindow() throws InterruptedException {
        RateLimitService service = new RateLimitService(properties(1, 1));

        assertTrue(service.tryConsume(RateLimitPolicy.API, "alice").allowed());
        assertFalse(service.tryConsume(RateLimitPolicy.API, "alice").allowed());
        Thread.sleep(1_100);
        assertTrue(service.tryConsume(RateLimitPolicy.API, "alice").allowed());
    }

    @Test
    void checkDoesNotTakeATokenButPenalizeDoes() {
        RateLimitService service = new RateLimitService(properties(1, 60));

        assertTrue(service.check(RateLimitPolicy.LOGIN_FAILURE, "account:1").allowed());
        assertTrue(service.check(RateLimitPolicy.LOGIN_FAILURE, "account:1").allowed());
        service.penalize(RateLimitPolicy.LOGIN_FAILURE, "account:1");

        RateLimitDecision decision = service.check(RateLimitPolicy.LOGIN_FAILURE, "account:1");
        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
    }

    @Test
    void configureAppliesTheNewQuotaAtOnceWithFreshBuckets() {
        RateLimitService service = new RateLimitService(properties(1, 60));
        assertTrue(service.tryConsume(RateLimitPolicy.API, "alice").allowed());
        assertFalse(service.tryConsume(RateLimitPolicy.API, "alice").allowed());

        service.configure(RateLimitPolicy.API, new RateLimitProperties.Quota(3, 60), true);

        RateLimitDecision decision = service.tryConsume(RateLimitPolicy.API, "alice");
        assertTrue(decision.allowed());
        assertEquals(3, decision.limit());
        assertEquals(2, decision.remaining());
        PolicyStats api = stats(service, RateLimitPolicy.API);
        assertTrue(api.customized());
        assertEquals(3, api.maxRequests());
        assertEquals(1, api.defaultMaxRequests());
    }

    @Test
    void aDisabledPolicyIsNotEnforcedAndRestoreDefaultSwitchesItBackOn() {
        RateLimitService service = new RateLimitService(properties(5, 60));

        service.configure(RateLimitPolicy.EXPORT, new RateLimitProperties.Quota(5, 60), false);
        assertFalse(service.enabled(RateLimitPolicy.EXPORT));
        assertTrue(service.enabled(RateLimitPolicy.API));

        service.restoreDefault(RateLimitPolicy.EXPORT);
        assertTrue(service.enabled(RateLimitPolicy.EXPORT));
        assertFalse(stats(service, RateLimitPolicy.EXPORT).customized());
    }

    @Test
    void theLoginFailurePolicyCannotBeDisabled() {
        RateLimitService service = new RateLimitService(properties(5, 60));

        assertThrows(IllegalArgumentException.class, () -> service.configure(
            RateLimitPolicy.LOGIN_FAILURE, new RateLimitProperties.Quota(5, 60), false));
        assertTrue(service.enabled(RateLimitPolicy.LOGIN_FAILURE));
    }

    @Test
    void refusesAPolicyWithoutAQuota() {
        assertThrows(IllegalStateException.class,
            () -> new RateLimitProperties(true, List.of(), Map.of()));
    }

    private static PolicyStats stats(RateLimitService service, RateLimitPolicy policy) {
        return service.stats().policies().stream()
            .filter(stats -> stats.policy().equals(policy.key()))
            .findFirst()
            .orElseThrow();
    }

    static RateLimitProperties properties(long maxRequests, long windowSeconds) {
        Map<String, RateLimitProperties.Quota> policies = new HashMap<>();
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            policies.put(policy.key(), new RateLimitProperties.Quota(maxRequests, windowSeconds));
        }
        return new RateLimitProperties(true, List.of(), policies);
    }
}
