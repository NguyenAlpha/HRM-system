package com.htttdn.hrm.service.auth;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An HRM user signs in with their username, their email or the code of their employee record. */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class EmployeeCodeLoginIntegrationTest {

    private static final String PASSWORD = "Correct@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void theEmployeeCodeSignsInLikeTheUsernameInAnyCase() throws Exception {
        String suffix = suffix();
        String username = "code_" + suffix;
        insertAccount(insertEmployee("EC" + suffix), username);

        login("EC" + suffix, PASSWORD).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.account.username").value(username));
        login(("ec" + suffix).toLowerCase(), PASSWORD).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.account.username").value(username));
        login("EC" + suffix, "wrong").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void aUsernameEqualToAnotherEmployeesCodeSignsInAsItself() throws Exception {
        String suffix = suffix();
        String code = "EC" + suffix;
        insertAccount(insertEmployee(code), "owner_of_code_" + suffix);
        // Someone else's username spells the first person's code.
        insertAccount(insertEmployee("EX" + suffix), code);

        login(code, PASSWORD).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.account.username").value(code));
    }

    @Test
    void theCodeSignsInToTheEmployeesLiveAccountNeverADeletedOne() throws Exception {
        String suffix = suffix();
        Long employeeId = insertEmployee("EC" + suffix);
        Long deletedAccountId = insertAccount(employeeId, "old_" + suffix);
        jdbc.update("""
            UPDATE accounts SET status = 'DISABLED', deleted_at = now(), deleted_by_account_id = id,
                deletion_reason = 'test' WHERE id = ?
            """, deletedAccountId);

        login("EC" + suffix, PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // The employee is given a new account.
        insertAccount(employeeId, "new_" + suffix);
        login("EC" + suffix, PASSWORD).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.account.username").value("new_" + suffix));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(login, password)));
    }

    private Long insertEmployee(String code) {
        return jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Code login', '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, Long.class, code);
    }

    private Long insertAccount(Long employeeId, String username) {
        return jdbc.queryForObject("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status)
            VALUES (?, ?, ?, ?, 'ACTIVE') RETURNING id
            """, Long.class, employeeId, username, username + "@hrm.local", passwordEncoder.encode(PASSWORD));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
