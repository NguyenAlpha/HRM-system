package com.htttdn.hrm.ratelimit;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationSnapshot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The per-user layer after JWT verification, with real signed tokens. */
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
    "rate-limit.policies.api.max-requests=3",
    "rate-limit.policies.export.max-requests=1"
})
@AutoConfigureMockMvc
class UserRateLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @Test
    void eachUserHasTheirOwnQuota() throws Exception {
        String alice = bearer(1L, "rl-alice");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(accounts(alice)).andExpect(status().isForbidden())
                .andExpect(header().string(RateLimitResponses.LIMIT, "3"));
        }
        mockMvc.perform(accounts(alice))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        mockMvc.perform(accounts(bearer(2L, "rl-bob"))).andExpect(status().isForbidden());
    }

    @Test
    void aSpecificRuleReplacesTheDefaultQuotaAndHeadSharesIt() throws Exception {
        String carol = bearer(3L, "rl-carol");
        mockMvc.perform(get("/api/payroll/periods/999999/export").header(HttpHeaders.AUTHORIZATION, carol))
            .andExpect(status().is4xxClientError())
            .andExpect(header().string(RateLimitResponses.LIMIT, "1"));
        mockMvc.perform(head("/api/payroll/periods/999999/export").header(HttpHeaders.AUTHORIZATION, carol))
            .andExpect(status().isTooManyRequests());

        // The export quota is separate: the default quota is still untouched.
        mockMvc.perform(accounts(carol)).andExpect(header().string(RateLimitResponses.REMAINING, "2"));
    }

    @Test
    void anInvalidTokenIsRejectedWithoutCounting() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(accounts(bearer(4L, "rl-dave")))
            .andExpect(header().string(RateLimitResponses.REMAINING, "2"));
    }

    private MockHttpServletRequestBuilder accounts(String bearer) {
        return get("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private String bearer(Long accountId, String username) {
        return "Bearer " + jwtService.generateAccessToken(
            Account.builder().id(accountId).username(username).build(),
            new AuthorizationSnapshot(List.of("EMPLOYEE"), List.of())
        );
    }
}
