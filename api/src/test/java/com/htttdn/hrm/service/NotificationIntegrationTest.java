package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.exception.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
    "company.seed.enabled=false", "rbac.seed.enabled=false", "admin.seed.enabled=false",
    "owner.seed.enabled=false", "staff.seed.enabled=false", "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false"
})
@Transactional
class NotificationIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationService service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inboxAndReadAreLimitedToRecipient() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long employeeId = jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Notification Applicant', '2026-01-01', '2026-01-01', 'ACTIVE') RETURNING id
            """, Long.class, "NOTIF-" + suffix);
        Long recipientId = insertAccount("notif_hr_" + suffix);
        Long otherId = insertAccount("notif_other_" + suffix);
        Long leaveId = jdbc.queryForObject("""
            INSERT INTO leave_requests(employee_id, leave_type, salary_treatment, start_at, end_at,
                requested_minutes, reason, status, submitted_at)
            VALUES (?, 'ANNUAL', 'EMPLOYER_PAID', '2026-11-02T01:00:00Z', '2026-11-02T10:00:00Z',
                480, 'Integration test', 'PENDING', now()) RETURNING id
            """, Long.class, employeeId);
        Long notificationId = jdbc.queryForObject("""
            INSERT INTO notifications(recipient_account_id, leave_request_id, type)
            VALUES (?, ?, 'LEAVE_SUBMITTED') RETURNING id
            """, Long.class, recipientId, leaveId);

        authenticateAs(recipientId);
        assertEquals(1, service.inbox().unreadCount());
        assertEquals(notificationId, service.inbox().items().getFirst().id());
        service.markRead(notificationId);
        assertEquals(0, service.inbox().unreadCount());

        authenticateAs(otherId);
        assertEquals(0, service.inbox().items().size());
        assertThrows(ResourceNotFoundException.class, () -> service.markRead(notificationId));
    }

    private Long insertAccount(String username) {
        return jdbc.queryForObject("""
            INSERT INTO accounts(username, email, password_hash, status)
            VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, username, username + "@hrm.local");
    }

    private void authenticateAs(Long accountId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("test", now, now.plusSeconds(600), Map.of("alg", "none"),
            Map.of("accountId", accountId));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
