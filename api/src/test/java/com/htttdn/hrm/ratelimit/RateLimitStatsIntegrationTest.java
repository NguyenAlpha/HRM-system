package com.htttdn.hrm.ratelimit;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;

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

    private String bearer(Long accountId, String username, List<String> permissions) {
        return "Bearer " + jwtService.generateAccessToken(
            Account.builder().id(accountId).username(username).build(),
            new AuthorizationSnapshot(List.of("SYSTEM_ADMIN"), permissions)
        );
    }
}
