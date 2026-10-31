package com.htttdn.hrm.ratelimit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Over a real HTTP connection, since Tomcat leaves the request URI encoded while Spring MVC decodes
 * it to find the handler: an encoded path must still get the quota of the handler it reaches.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=true",
    "rate-limit.policies.login.max-requests=2",
    "rate-limit.policies.login-failure.max-requests=100"
})
class EncodedPathRateLimitIntegrationTest {

    @LocalServerPort private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void encodedLoginPathsShareTheLoginQuota() throws Exception {
        assertEquals(401, login("/api/auth/login").statusCode());
        HttpResponse<String> encoded = login("/api/auth/%6Cogin");
        assertEquals(401, encoded.statusCode());
        assertEquals("2", encoded.headers().firstValue(RateLimitResponses.LIMIT).orElse(null));
        assertEquals(429, login("/%61pi/auth/login").statusCode());
    }

    @Test
    void encodedPathsGetTheUserQuotaOfTheirHandler() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/%61pi/reports/hr/turnover");

        assertEquals(RateLimitPolicy.REPORT, UserRateLimitFilter.policy(request));
    }

    private HttpResponse<String> login(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"usernameOrEmail\":\"nobody\",\"password\":\"wrong-password\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
