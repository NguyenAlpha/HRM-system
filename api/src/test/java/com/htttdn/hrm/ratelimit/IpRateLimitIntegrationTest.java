package com.htttdn.hrm.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The per-IP layer in front of Spring Security, with the real filter chain. */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=true",
    "rate-limit.trusted-proxies=127.0.0.1",
    "rate-limit.policies.login.max-requests=2",
    "rate-limit.policies.ip-ceiling.max-requests=3",
    "rate-limit.policies.login-failure.max-requests=100",
    "cors.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
class IpRateLimitIntegrationTest {

    private static final String ORIGIN = "http://localhost:3000";

    @Autowired private MockMvc mockMvc;

    @Test
    void loginIsLimitedPerIpWithCorsAndRetryHeaders() throws Exception {
        mockMvc.perform(login("198.51.100.10")).andExpect(status().isUnauthorized())
            .andExpect(header().string(RateLimitResponses.LIMIT, "2"))
            .andExpect(header().string(RateLimitResponses.REMAINING, "1"));
        mockMvc.perform(login("198.51.100.10")).andExpect(status().isUnauthorized());

        mockMvc.perform(login("198.51.100.10").header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(header().string(RateLimitResponses.REMAINING, "0"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));

        mockMvc.perform(login("198.51.100.11")).andExpect(status().isUnauthorized());
    }

    @Test
    void otherRequestsShareTheIpCeiling() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(branding("198.51.100.20")).andExpect(status().isOk());
        }
        mockMvc.perform(branding("198.51.100.20")).andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/company").with(from("198.51.100.20"))).andExpect(status().isTooManyRequests());
        mockMvc.perform(branding("198.51.100.21")).andExpect(status().isOk());
    }

    @Test
    void preflightIsNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/api/auth/login").with(from("198.51.100.30"))
                    .header(HttpHeaders.ORIGIN, ORIGIN)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk());
        }
        mockMvc.perform(login("198.51.100.30")).andExpect(status().isUnauthorized());
    }

    @Test
    void trustedProxyForwardsTheClientButOthersCannotSpoofIt() throws Exception {
        mockMvc.perform(login("127.0.0.1").header("X-Forwarded-For", "198.51.100.40")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("127.0.0.1").header("X-Forwarded-For", "198.51.100.40")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("127.0.0.1").header("X-Forwarded-For", "198.51.100.40")).andExpect(status().isTooManyRequests());
        mockMvc.perform(login("127.0.0.1").header("X-Forwarded-For", "198.51.100.41")).andExpect(status().isUnauthorized());

        mockMvc.perform(login("198.51.100.50").header("X-Forwarded-For", "1.1.1.1")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("198.51.100.50").header("X-Forwarded-For", "2.2.2.2")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("198.51.100.50").header("X-Forwarded-For", "3.3.3.3")).andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return post("/api/auth/login").with(from(ip))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"usernameOrEmail\":\"nobody\",\"password\":\"wrong-password\"}");
    }

    private MockHttpServletRequestBuilder branding(String ip) {
        return get("/api/public/branding").with(from(ip));
    }

    static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
