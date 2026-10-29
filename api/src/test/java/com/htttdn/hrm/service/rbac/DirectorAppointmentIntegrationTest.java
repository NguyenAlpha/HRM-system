package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AppointDirectorRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Company Owner appoints the next Director in one step, on a handover day that may lie ahead: the
 * current Director works until the day before. Revokes the seeded Director inside a rolled-back
 * transaction, so it runs on an empty or seeded database.
 */
@SpringBootTest
@Transactional
class DirectorAppointmentIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;
    @Autowired private AccountRoleAssignmentAdminService adminService;

    private Long ownerId;
    private Long directorId;
    private Long directorAssignmentId;
    private Long successorId;
    private LocalDate today;

    @BeforeEach
    void seed() {
        today = LocalDate.now(clock);
        ownerId = account("owner", null);
        directorId = account("director", employee());
        successorId = account("successor", employee());
        // Only this test's Director counts.
        jdbc.update("""
            UPDATE account_role_assignments SET revoked_at = now(), revoked_by_account_id = ?, revocation_reason = 'test'
            WHERE revoked_at IS NULL AND role_id IN (SELECT id FROM roles WHERE code = 'DIRECTOR')
            """, ownerId);
        directorAssignmentId = directorAssignment(directorId, "2025-01-01");
        authenticateAs(ownerId, "account.role.assign");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aHandoverAheadKeepsTheCurrentDirectorUntilTheDayBefore() {
        LocalDate handover = today.plusDays(10);

        var appointed = adminService.appointDirector(successorId, new AppointDirectorRequest(handover, " Bổ nhiệm "));
        entityManager.flush();

        assertEquals(handover, appointed.effectiveFrom());
        assertNull(appointed.effectiveTo());
        assertEquals("Bổ nhiệm", appointed.reason());
        assertEquals(handover.minusDays(1), effectiveTo(directorAssignmentId));
        // Not revoked: the current Director keeps the role until then.
        assertNull(jdbc.queryForObject(
            "SELECT revoked_at FROM account_role_assignments WHERE id = ?", Object.class, directorAssignmentId));
    }

    @Test
    void aHandoverTodayEndsTheCurrentDirectorYesterday() {
        adminService.appointDirector(successorId, new AppointDirectorRequest(today, "Bổ nhiệm"));
        entityManager.flush();

        assertEquals(today.minusDays(1), effectiveTo(directorAssignmentId));
    }

    @Test
    void theHandoverIsNotInThePastNorToTheCurrentDirector() {
        BusinessException past = assertThrows(BusinessException.class, () -> adminService.appointDirector(
            successorId, new AppointDirectorRequest(today.minusDays(1), "x")));
        assertEquals("handoverDate", past.getField());

        BusinessException same = assertThrows(BusinessException.class, () -> adminService.appointDirector(
            directorId, new AppointDirectorRequest(today.plusDays(1), "x")));
        assertEquals("accountId", same.getField());
        assertNull(effectiveTo(directorAssignmentId));
    }

    @Test
    void anAppointmentAlreadyScheduledFromThatDayIsNotReplaced() {
        Long otherId = account("other", employee());
        directorAssignment(otherId, today.plusDays(5).toString());
        jdbc.update("UPDATE account_role_assignments SET effective_to = ? WHERE id = ?",
            today.plusDays(4), directorAssignmentId);

        ConflictException exception = assertThrows(ConflictException.class, () -> adminService.appointDirector(
            successorId, new AppointDirectorRequest(today.plusDays(3), "x")));

        assertEquals(ErrorCode.DIRECTOR_ALREADY_ASSIGNED, exception.getErrorCode());
        assertEquals(today.plusDays(4), effectiveTo(directorAssignmentId));
    }

    private LocalDate effectiveTo(Long assignmentId) {
        return jdbc.queryForObject(
            "SELECT effective_to FROM account_role_assignments WHERE id = ?", LocalDate.class, assignmentId);
    }

    private Long directorAssignment(Long accountId, String from) {
        return jdbc.queryForObject("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', CAST(? AS date), ? FROM roles WHERE code = 'DIRECTOR' RETURNING id
            """, Long.class, accountId, from, ownerId);
    }

    private Long employee() {
        return jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Director Candidate', '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, Long.class, "DIR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private Long account(String prefix, Long employeeId) {
        String username = "dir_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status)
            VALUES (?, ?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, employeeId, username, username + "@hrm.local");
    }

    private void authenticateAs(Long accountId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("director-it").header("alg", "none").claim("accountId", accountId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
