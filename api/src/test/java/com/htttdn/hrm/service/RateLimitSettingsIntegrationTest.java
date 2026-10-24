package com.htttdn.hrm.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.ratelimit.RateLimitPolicy;
import com.htttdn.hrm.ratelimit.RateLimitService;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT/DELETE /api/system/rate-limit/policies/{policy}: who may change a quota, that the change is
 * enforced at once, stored, replayed on start, and that login-failure cannot be switched off.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=true",
    "rate-limit.policies.ip-ceiling.max-requests=1000",
    "rate-limit.policies.api.max-requests=50"
})
@AutoConfigureMockMvc
@Transactional
class RateLimitSettingsIntegrationTest {

    private static final String API_POLICY = "/api/system/rate-limit/policies/api";
    private static final AtomicInteger USERNAMES = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RateLimitService rateLimitService;
    @Autowired private RateLimitSettingsService rateLimitSettingsService;

    private Long adminId;

    @BeforeEach
    void insertAdmin() {
        String username = "rl-settings-" + System.nanoTime();
        adminId = jdbc.queryForObject("""
            INSERT INTO accounts(username, email, status) VALUES (?, ?, 'PENDING') RETURNING id
            """, Long.class, username, username + "@hrm.local");
    }

    /** The buckets live outside the rolled-back transaction, so every policy goes back to its default. */
    @AfterEach
    void restoreDefaults() {
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            rateLimitService.restoreDefault(policy);
        }
    }

    @Test
    void needsTheManagePermission() throws Exception {
        mockMvc.perform(put(API_POLICY).contentType(MediaType.APPLICATION_JSON).content(body(true, 5, 60)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put(API_POLICY).header(HttpHeaders.AUTHORIZATION, bearer(List.of("system.rate_limit.read")))
                .contentType(MediaType.APPLICATION_JSON).content(body(true, 5, 60)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(API_POLICY).header(HttpHeaders.AUTHORIZATION, bearer(List.of("system.rate_limit.read"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void aNewQuotaIsEnforcedAtOnceAndStored() throws Exception {
        update("api", true, 1, 60)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].maxRequests").value(contains(1)))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].defaultMaxRequests").value(contains(50)))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].customized").value(contains(true)));

        String user = bearer(List.of());
        mockMvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, user))
            .andExpect(header().string("RateLimit-Limit", "1"));
        mockMvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, user))
            .andExpect(status().isTooManyRequests());

        Integer stored = jdbc.queryForObject(
            "SELECT max_requests FROM rate_limit_policy_settings WHERE policy = 'api' AND updated_by_account_id = ?",
            Integer.class, adminId);
        assertEquals(1, stored);
    }

    @Test
    void aDisabledPolicyLetsEveryRequestThrough() throws Exception {
        update("api", false, 1, 60).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].enabled").value(contains(false)));

        String user = bearer(List.of());
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden())
                // Only the per-IP ceiling still answers with its headers.
                .andExpect(header().string("RateLimit-Limit", "1000"));
        }
    }

    @Test
    void restoringTheDefaultDropsTheStoredChange() throws Exception {
        update("api", false, 1, 60).andExpect(status().isOk());

        mockMvc.perform(delete(API_POLICY).header(HttpHeaders.AUTHORIZATION, manager()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].maxRequests").value(contains(50)))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].enabled").value(contains(true)))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].customized").value(contains(false)));
        Integer rows = jdbc.queryForObject(
            "SELECT count(*) FROM rate_limit_policy_settings WHERE policy = 'api'", Integer.class);
        assertEquals(0, rows);
    }

    @Test
    void storedChangesAreReplayedOnStart() throws Exception {
        update("export", false, 3, 120).andExpect(status().isOk());
        rateLimitService.restoreDefault(RateLimitPolicy.EXPORT);

        rateLimitSettingsService.applyStoredSettings();

        mockMvc.perform(get("/api/system/rate-limit/stats").header(HttpHeaders.AUTHORIZATION, manager()))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'export')].maxRequests").value(contains(3)))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'export')].windowSeconds").value(contains(120)))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'export')].enabled").value(contains(false)));
    }

    @Test
    void theLoginFailurePolicyCannotBeSwitchedOff() throws Exception {
        update("login-failure", false, 5, 900)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_POLICY_REQUIRED"));
        update("login-failure", true, 3, 900)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'login-failure')].canDisable").value(contains(false)))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'login-failure')].maxRequests").value(contains(3)));
    }

    @Test
    void rejectsUnknownPoliciesAndOutOfRangeValues() throws Exception {
        update("nope", true, 5, 60)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_POLICY_NOT_FOUND"));
        update("api", true, 0, 60)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        update("api", true, 5, 86_401)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private ResultActions update(String policy, boolean enabled, int maxRequests, int windowSeconds) throws Exception {
        return mockMvc.perform(put("/api/system/rate-limit/policies/{policy}", policy)
            .header(HttpHeaders.AUTHORIZATION, manager())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(enabled, maxRequests, windowSeconds)));
    }

    private static String body(boolean enabled, int maxRequests, int windowSeconds) {
        return "{\"enabled\":%s,\"maxRequests\":%d,\"windowSeconds\":%d}".formatted(enabled, maxRequests, windowSeconds);
    }

    /** The administrator, under a new username each time so a tight api quota never blocks the test itself. */
    private String manager() {
        return token(adminId, List.of("system.rate_limit.read", "system.rate_limit.manage"));
    }

    private String bearer(List<String> permissions) {
        return token(900_000L + USERNAMES.incrementAndGet(), permissions);
    }

    private String token(Long accountId, List<String> permissions) {
        return "Bearer " + jwtService.generateAccessToken(
            Account.builder().id(accountId).username("rl-user-" + USERNAMES.incrementAndGet()).build(),
            new AuthorizationSnapshot(List.of("SYSTEM_ADMIN"), permissions)
        );
    }
}
