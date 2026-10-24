package com.htttdn.hrm.controller;

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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/system/traffic/stats: who may read it, what it counts, and no /actuator exposure. */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false"
})
@AutoConfigureMockMvc
class ApiTrafficIntegrationTest {

    private static final String STATS = "/api/system/traffic/stats";
    private static final String TIMELINE = "/api/system/traffic/timeline";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @Test
    void needsTheTrafficPermission() throws Exception {
        mockMvc.perform(get(STATS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(STATS).header(HttpHeaders.AUTHORIZATION, bearer(List.of("system.rate_limit.read"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void countsRequestsAndErrorsPerRoute() throws Exception {
        String nobody = bearer(List.of());
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/accounts/{id}", 42 + i).header(HttpHeaders.AUTHORIZATION, nobody))
                .andExpect(status().isForbidden());
        }

        String route = "$.data.endpoints[?(@.method == 'GET' && @.uri == '/api/accounts/{accountId}')]";
        mockMvc.perform(get(STATS).header(HttpHeaders.AUTHORIZATION, bearer(List.of("system.traffic.read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.startedAt").exists())
            .andExpect(jsonPath("$.data.totalRequests").value(greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.data.clientErrors").value(greaterThanOrEqualTo(3)))
            .andExpect(jsonPath(route + ".requests").value(hasItem(greaterThanOrEqualTo(3))))
            .andExpect(jsonPath(route + ".clientErrors").value(hasItem(greaterThanOrEqualTo(3))))
            .andExpect(jsonPath(route + ".meanMillis").exists());
    }

    @Test
    void reportsTheWholeApiOverTime() throws Exception {
        String nobody = bearer(List.of());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, nobody))
                .andExpect(status().isForbidden());
        }
        String reader = bearer(List.of("system.traffic.read"));

        mockMvc.perform(get(TIMELINE).header(HttpHeaders.AUTHORIZATION, bearer(List.of("system.rate_limit.read"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(TIMELINE).header(HttpHeaders.AUTHORIZATION, reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.range").value("HOUR"))
            .andExpect(jsonPath("$.data.slotSeconds").value(60))
            .andExpect(jsonPath("$.data.points.length()").value(60))
            // Summed, so the test holds when the requests and the read fall in different minutes.
            .andExpect(result -> assertTrue(sum(result, "$.data.points[*].requests") >= 2))
            .andExpect(result -> assertTrue(sum(result, "$.data.points[*].clientErrors") >= 2));
        mockMvc.perform(get(TIMELINE).param("range", "DAY").header(HttpHeaders.AUTHORIZATION, reader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.points.length()").value(96));
        mockMvc.perform(get(TIMELINE).param("range", "YEAR").header(HttpHeaders.AUTHORIZATION, reader))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void actuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(List.of("system.traffic.read"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(List.of("system.traffic.read"))))
            .andExpect(status().isNotFound());
    }

    private static long sum(MvcResult result, String path) throws Exception {
        List<Number> values = JsonPath.read(result.getResponse().getContentAsString(), path);
        return values.stream().mapToLong(Number::longValue).sum();
    }

    private String bearer(List<String> permissions) {
        return "Bearer " + jwtService.generateAccessToken(
            Account.builder().id(31L).username("traffic-test").build(),
            new AuthorizationSnapshot(List.of("SYSTEM_ADMIN"), permissions)
        );
    }
}
