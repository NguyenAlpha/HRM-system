package com.htttdn.hrm.ratelimit;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.htttdn.hrm.entity.Account;
import com.jayway.jsonpath.JsonPath;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/system/rate-limit/stats: who may read it and what it reports. */
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
    "rate-limit.policies.api.max-requests=2"
})
@AutoConfigureMockMvc
class RateLimitStatsIntegrationTest {

    private static final String STATS = "/api/system/rate-limit/stats";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @Test
    void needsTheStatisticsPermission() throws Exception {
        mockMvc.perform(get(STATS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(STATS).header(HttpHeaders.AUTHORIZATION, bearer(11L, "stats-nobody", List.of())))
            .andExpect(status().isForbidden());
    }

    @Test
    void reportsQuotasCountersAndTheMostBlockedKeys() throws Exception {
        String spammer = bearer(12L, "stats-spammer", List.of());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, spammer));
        }

        mockMvc.perform(get(STATS).header(HttpHeaders.AUTHORIZATION,
                bearer(13L, "stats-admin", List.of("system.rate_limit.read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.startedAt").exists())
            .andExpect(jsonPath("$.data.policies.length()").value(RateLimitPolicy.values().length))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].maxRequests").value(2))
            .andExpect(jsonPath("$.data.policies[?(@.policy == 'api')].blocked").value(3))
            .andExpect(jsonPath("$.data.topBlocked[0].key").value("stats-spammer"))
            .andExpect(jsonPath("$.data.topBlocked[0].scope").value("USER"))
            .andExpect(jsonPath("$.data.topBlocked[0].blocked").value(3));
    }

    @Test
    void reportsTheTimelineOfAPolicyOrOfAllOfThem() throws Exception {
        String spammer = bearer(14L, "timeline-spammer", List.of());
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, spammer));
        }
        String reader = bearer(15L, "timeline-admin", List.of("system.rate_limit.read"));

        mockMvc.perform(get("/api/system/rate-limit/timeline").header(HttpHeaders.AUTHORIZATION,
                bearer(16L, "timeline-nobody", List.of())))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/system/rate-limit/timeline").param("policy", "api")
                .header(HttpHeaders.AUTHORIZATION, reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.range").value("HOUR"))
            .andExpect(jsonPath("$.data.policy").value("api"))
            .andExpect(jsonPath("$.data.slotSeconds").value(60))
            .andExpect(jsonPath("$.data.points.length()").value(60))
            // Summed, so the test holds when the requests and the read fall in different minutes.
            .andExpect(result -> assertTrue(sum(result, "$.data.points[*].blocked") >= 2));
        mockMvc.perform(get("/api/system/rate-limit/timeline").param("range", "WEEK")
                .header(HttpHeaders.AUTHORIZATION, reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy").doesNotExist())
            .andExpect(jsonPath("$.data.slotSeconds").value(3600))
            .andExpect(jsonPath("$.data.points.length()").value(168))
            .andExpect(result -> assertTrue(sum(result, "$.data.points[*].requests") >= 4));
        mockMvc.perform(get("/api/system/rate-limit/timeline").param("range", "YEAR")
                .header(HttpHeaders.AUTHORIZATION, reader))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/system/rate-limit/timeline").param("policy", "nope")
                .header(HttpHeaders.AUTHORIZATION, reader))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_POLICY_NOT_FOUND"));
    }

    private static long sum(MvcResult result, String path) throws Exception {
        List<Number> values = JsonPath.read(result.getResponse().getContentAsString(), path);
        return values.stream().mapToLong(Number::longValue).sum();
    }

    private String bearer(Long accountId, String username, List<String> permissions) {
        return "Bearer " + jwtService.generateAccessToken(
            Account.builder().id(accountId).username(username).build(),
            new AuthorizationSnapshot(List.of("SYSTEM_ADMIN"), permissions)
        );
    }
}
