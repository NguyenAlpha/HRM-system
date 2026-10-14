package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employeerequest.CreateResignationRequestRequest;
import com.htttdn.hrm.entity.enums.ResignationRequestStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.service.ResignationRequestService.ResignationRequestView;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A resignation goes to the direct manager like leave; employment only ends once the approved last
 * working day has passed. Runs on an empty or seeded database inside a rolled-back transaction.
 */
@SpringBootTest
@Transactional
class ResignationRequestIntegrationTest {

    private static final String[] EMPLOYEE = { "request.self.read", "request.self.create", "request.self.cancel" };

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private ResignationRequestService service;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    private Long unitId;
    private Long locationId;
    private Long positionId;
    private Long shiftId;
    private Long creatorAccountId;
    private Person staff;
    private Person manager;

    private record Person(Long employeeId, Long accountId) {
    }

    @BeforeEach
    void seed() {
        String suffix = suffix();
        unitId = id("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Resign unit', 'DEPARTMENT') RETURNING id",
            "RSU_" + suffix);
        locationId = id("""
            INSERT INTO work_locations(code, name, location_type, address) VALUES (?, 'Resign', 'HEAD_OFFICE', 'HN') RETURNING id
            """, "RSL_" + suffix);
        positionId = id("INSERT INTO job_positions(code, title) VALUES (?, 'Resign role') RETURNING id", "RSP_" + suffix);
        shiftId = id("""
            INSERT INTO work_shifts(code, name, start_time, end_time, break_minutes, break_start_time, standard_work_minutes)
            VALUES (?, 'Ca hành chính', '08:00', '17:00', 60, '12:00', 480) RETURNING id
            """, "RSS_" + suffix);
        creatorAccountId = id("""
            INSERT INTO accounts(username, email, password_hash, status) VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, "rs_creator_" + suffix, "rs_creator_" + suffix + "@hrm.local");
        manager = person("manager", null);
        staff = person("staff", manager.employeeId());
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2025-01-01', ? FROM roles WHERE code = 'HR_MANAGER'
            """, staff.accountId(), creatorAccountId);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theDirectManagerDecidesAndMayMoveTheLastWorkingDay() {
        ResignationRequestView pending = resign(staff, today.plusDays(10));
        assertEquals(ResignationRequestStatus.PENDING, pending.status());
        assertEquals(manager.employeeId(), pending.approverEmployeeId());

        authenticateAs(staff, EMPLOYEE);
        assertThrows(ForbiddenException.class, () -> service.approve(pending.id(), null, null));

        authenticateAs(manager, EMPLOYEE);
        assertEquals(pending.id(), service.listPending(Pageable.unpaged()).getContent().getFirst().id());
        assertThrows(BusinessException.class, () -> service.approve(pending.id(), null, today.minusDays(1)));
        ResignationRequestView approved = service.approve(pending.id(), "Đồng ý", today.plusDays(30));

        assertEquals(ResignationRequestStatus.APPROVED, approved.status());
        assertEquals(today.plusDays(30), approved.lastWorkingDate());
        // Nothing ends before the last working day.
        assertEquals("ACTIVE", employeeStatus(staff));
        assertTrue(service.dueIds(today).stream().noneMatch(pending.id()::equals));
    }

    @Test
    void employmentEndsTheDayAfterTheLastWorkingDay() {
        ResignationRequestView pending = resign(staff, today);
        authenticateAs(manager, EMPLOYEE);
        service.approve(pending.id(), null, null);
        SecurityContextHolder.clearContext();

        assertTrue(service.dueIds(today.plusDays(1)).contains(pending.id()));
        // The job runs on the next day; pretend that day has come by moving the request back one day.
        jdbc.update("UPDATE resignation_requests SET last_working_date = ? WHERE id = ?", today.minusDays(1), pending.id());
        entityManager.clear();
        service.complete(pending.id());
        entityManager.flush();

        assertEquals("RESIGNED", employeeStatus(staff));
        assertEquals(today.minusDays(1), jdbc.queryForObject(
            "SELECT termination_date FROM employees WHERE id = ?", LocalDate.class, staff.employeeId()));
        assertEquals("DISABLED", jdbc.queryForObject(
            "SELECT status FROM accounts WHERE id = ?", String.class, staff.accountId()));
        assertEquals(0, jdbc.queryForObject("""
            SELECT count(*) FROM account_role_assignments ra JOIN roles r ON r.id = ra.role_id
            WHERE ra.account_id = ? AND r.code = 'HR_MANAGER' AND ra.revoked_at IS NULL
            """, Integer.class, staff.accountId()));
        assertNotNull(jdbc.queryForObject(
            "SELECT completed_at FROM resignation_requests WHERE id = ?", java.sql.Timestamp.class, pending.id()));
    }

    @Test
    void oneOpenRequestAtATimeAndARejectionNeedsAReason() {
        ResignationRequestView pending = resign(staff, today.plusDays(5));
        assertThrows(ConflictException.class, () -> resign(staff, today.plusDays(6)));

        authenticateAs(manager, EMPLOYEE);
        assertThrows(BusinessException.class, () -> service.reject(pending.id(), " "));
        assertEquals(ResignationRequestStatus.REJECTED, service.reject(pending.id(), "Chưa bàn giao xong").status());

        ResignationRequestView again = resign(staff, today.plusDays(7));
        authenticateAs(staff, EMPLOYEE);
        assertEquals(ResignationRequestStatus.CANCELLED, service.cancel(again.id()).status());
        assertThrows(ConflictException.class, () -> service.cancel(again.id()));
    }

    @Test
    void theLastWorkingDayCannotBeInThePast() {
        assertThrows(BusinessException.class, () -> resign(staff, today.minusDays(1)));
    }

    private ResignationRequestView resign(Person person, LocalDate lastWorkingDate) {
        authenticateAs(person, EMPLOYEE);
        return service.create(new CreateResignationRequestRequest(person.employeeId(), lastWorkingDate, "Chuyển công tác"));
    }

    private String employeeStatus(Person person) {
        return jdbc.queryForObject("SELECT employment_status FROM employees WHERE id = ?", String.class, person.employeeId());
    }

    private Person person(String prefix, Long managerEmployeeId) {
        String suffix = suffix();
        Long employeeId = id("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, ?, '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, "RS" + suffix, "Resign " + prefix);
        jdbc.update("""
            INSERT INTO employee_assignments(employee_id, organization_unit_id, work_location_id, position_id,
                shift_id, manager_employee_id, employment_type, effective_from, created_by_account_id)
            VALUES (?, ?, ?, ?, ?, ?, 'FULL_TIME', '2025-01-01', ?)
            """, employeeId, unitId, locationId, positionId, shiftId, managerEmployeeId, creatorAccountId);
        Long accountId = id("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status) VALUES (?, ?, ?, 'x', 'ACTIVE') RETURNING id
            """, employeeId, "rs_" + prefix + "_" + suffix, "rs_" + prefix + "_" + suffix + "@hrm.local");
        return new Person(employeeId, accountId);
    }

    private Long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void authenticateAs(Person person, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("resign-it")
            .header("alg", "none")
            .claim("accountId", person.accountId())
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
