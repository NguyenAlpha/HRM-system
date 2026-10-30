package com.htttdn.hrm.ratelimit;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wrong passwords per account, checked in AuthService.login. */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=true",
    "rate-limit.policies.login.max-requests=1000",
    "rate-limit.policies.login-failure.max-requests=3"
})
@AutoConfigureMockMvc
@Transactional
class LoginAttemptLimiterIntegrationTest {

    private static final String PASSWORD = "Correct@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private String username;
    private String email;

    @BeforeEach
    void account() {
        username = "rl_" + UUID.randomUUID().toString().substring(0, 8);
        email = username + "@hrm.local";
        insertAccount(username, email, "ACTIVE");
    }

    @Test
    void wrongPasswordsRunOutTheTriesAndThenEvenTheRightOneWaits() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(username, "wrong").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }
        login(username, PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void successfulSignInsDoNotUseTries() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(username, PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void usernameAndEmailShareOneBucket() throws Exception {
        login(username, "wrong").andExpect(status().isUnauthorized());
        login(email, "wrong").andExpect(status().isUnauthorized());
        login(email.toUpperCase(), "wrong").andExpect(status().isUnauthorized());
        login(username, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownNamesAreLimitedRegardlessOfCase() throws Exception {
        String ghost = "ghost_" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            login(ghost, "wrong").andExpect(status().isUnauthorized());
        }
        login(ghost.toUpperCase(), "wrong").andExpect(status().isTooManyRequests());
    }

    @Test
    void aDisabledAccountIsToldOnlyToWhoeverKnowsThePassword() throws Exception {
        String disabled = "rl_disabled_" + UUID.randomUUID().toString().substring(0, 8);
        insertAccount(disabled, disabled + "@hrm.local", "DISABLED");
        // A wrong password reads like any other, so guessing usernames reveals no account status.
        login(disabled, "wrong").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        login(disabled, PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(login, password)));
    }

    private void insertAccount(String name, String mail, String status) {
        jdbc.update("""
            INSERT INTO accounts(username, email, password_hash, status)
            VALUES (?, ?, ?, ?)
            """, name, mail, passwordEncoder.encode(PASSWORD), status);
    }
}
