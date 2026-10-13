package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.response.attendance.OvertimeApprovalItemResponse;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.service.LeaveRequestService.LeaveRequestView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leave and overtime go to the direct manager, else the Director, else the Company Owner; nobody
 * approves their own, and HR only checks. Replaces the seeded Director/Owner inside a rolled-back
 * transaction, so it runs on an empty or seeded database.
 */
@SpringBootTest
@Transactional
class ApprovalChainIntegrationTest {

    private static final LocalDate LEAVE_DAY = LocalDate.of(2026, 3, 2);

    private static final String[] EMPLOYEE = { "request.self.read", "request.self.create", "request.self.cancel" };

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LeaveRequestService leaveRequestService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceCalendarService calendarService;
    @Autowired private WorkShiftRepository workShiftRepository;

    private Long unitId;
    private Long locationId;
    private Long positionId;
    private Long shiftId;
    private Long creatorAccountId;

    private Person staff;
    private Person manager;
    private Person director;
    private Person owner;
    private Person hr;

    private record Person(Long employeeId, Long accountId) {
    }

    @BeforeEach
    void seed() {
        String suffix = suffix();
        unitId = id("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Approval unit', 'DEPARTMENT') RETURNING id",
            "APU_" + suffix);
        locationId = id("""
            INSERT INTO work_locations(code, name, location_type, address) VALUES (?, 'Approval', 'HEAD_OFFICE', 'HN') RETURNING id
            """, "APL_" + suffix);
        positionId = id("INSERT INTO job_positions(code, title) VALUES (?, 'Approval role') RETURNING id", "APP_" + suffix);
        // Saved through JPA: a TIME column written over raw JDBC reads back shifted.
        Instant now = Instant.now();
        shiftId = workShiftRepository.saveAndFlush(WorkShift.builder()
            .code("APS_" + suffix).name("Ca hành chính")
            .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
            .breakMinutes(60).breakStartTime(LocalTime.of(12, 0))
            .standardWorkMinutes(480).graceLateMinutes(0).crossesMidnight(false).isActive(true)
            .createdAt(now).updatedAt(now)
            .build()).getId();
        creatorAccountId = id("""
            INSERT INTO accounts(username, email, password_hash, status) VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, "ap_creator_" + suffix, "ap_creator_" + suffix + "@hrm.local");

        // Only this test's Director and Company Owner count.
        jdbc.update("""
            UPDATE account_role_assignments SET revoked_at = now(), revoked_by_account_id = ?, revocation_reason = 'test'
            WHERE revoked_at IS NULL AND role_id IN (SELECT id FROM roles WHERE code IN ('DIRECTOR', 'COMPANY_OWNER'))
            """, creatorAccountId);

        owner = person("owner", null);
        director = person("director", null);
        manager = person("manager", null);
        staff = person("staff", manager.employeeId());
        hr = person("hr", manager.employeeId());
        grant(director, "DIRECTOR");
        grant(owner, "COMPANY_OWNER");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directManagerDecidesAndNobodyElseCan() {
        LeaveRequestView pending = submitLeave(staff);

        assertEquals(LeaveRequestStatus.PENDING, pending.status());
        assertEquals(manager.employeeId(), pending.approverEmployeeId());

        authenticateAs(hr, "request.read", "request.manage", "attendance.read", "attendance.manage");
        assertThrows(ForbiddenException.class, () -> leaveRequestService.approve(pending.id(), null));
        authenticateAs(staff, EMPLOYEE);
        assertThrows(ForbiddenException.class, () -> leaveRequestService.approve(pending.id(), null));
        authenticateAs(director, EMPLOYEE);
        assertThrows(ForbiddenException.class, () -> leaveRequestService.approve(pending.id(), null));

        authenticateAs(manager, EMPLOYEE);
        assertEquals(pending.id(), leaveRequestService.listPending(org.springframework.data.domain.Pageable.unpaged())
            .getContent().getFirst().id());
        assertEquals(LeaveRequestStatus.APPROVED, leaveRequestService.approve(pending.id(), "OK").status());
    }

    @Test
    void withoutAManagerTheDirectorDecides() {
        jdbc.update("UPDATE employee_assignments SET manager_employee_id = NULL WHERE employee_id = ?", staff.employeeId());

        assertEquals(director.employeeId(), submitLeave(staff).approverEmployeeId());
    }

    @Test
    void aManagerWhoLeftOrCannotSignInIsSkipped() {
        jdbc.update("UPDATE employees SET employment_status = 'RESIGNED' WHERE id = ?", manager.employeeId());
        assertEquals(director.employeeId(), submitLeave(staff).approverEmployeeId());

        jdbc.update("UPDATE employees SET employment_status = 'ACTIVE' WHERE id = ?", manager.employeeId());
        jdbc.update("UPDATE accounts SET status = 'DISABLED' WHERE id = ?", manager.accountId());
        assertEquals(director.employeeId(), submitLeave(hr).approverEmployeeId());
    }

    @Test
    void withoutADirectorTheCompanyOwnerDecides() {
        jdbc.update("UPDATE employee_assignments SET manager_employee_id = NULL WHERE employee_id = ?", staff.employeeId());
        jdbc.update("""
            UPDATE account_role_assignments SET revoked_at = now(), revoked_by_account_id = ?, revocation_reason = 'test'
            WHERE account_id = ? AND revoked_at IS NULL
            """, creatorAccountId, director.accountId());

        assertEquals(owner.employeeId(), submitLeave(staff).approverEmployeeId());
    }

    @Test
    void directorAndOwnerLeaveIsRecordedWithoutApproval() {
        LeaveRequestView directorLeave = submitLeave(director);
        LeaveRequestView ownerLeave = submitLeave(owner);

        assertEquals(LeaveRequestStatus.APPROVED, directorLeave.status());
        assertNull(directorLeave.approverEmployeeId());
        assertTrue(directorLeave.reviewComment().startsWith("Tự ghi nhận"));
        assertEquals(LeaveRequestStatus.APPROVED, ownerLeave.status());
    }

    @Test
    void overtimeGoesToTheSameApprover() {
        Long staffDay = overtimeDay(staff);
        Long directorDay = overtimeDay(director);
        Long ownerDay = overtimeDay(owner);
        ApproveOvertimeRequest noOvertime = new ApproveOvertimeRequest(0, BigDecimal.ONE);

        authenticateAs(hr, "attendance.read", "attendance.manage");
        assertThrows(ForbiddenException.class, () -> attendanceService.approveOvertime(staffDay, noOvertime));
        authenticateAs(staff, EMPLOYEE);
        assertThrows(ForbiddenException.class, () -> attendanceService.approveOvertime(staffDay, noOvertime));
        authenticateAs(director, EMPLOYEE);
        assertThrows(ForbiddenException.class, () -> attendanceService.approveOvertime(directorDay, noOvertime));

        authenticateAs(manager, EMPLOYEE);
        assertEquals(staffDay, attendanceService.listOvertimeAwaitingDecision().stream()
            .map(OvertimeApprovalItemResponse::record).filter(record -> record.id().equals(staffDay))
            .findFirst().orElseThrow().id());
        attendanceService.approveOvertime(staffDay, noOvertime);
        assertTrue(attendanceService.listOvertimeAwaitingDecision().stream()
            .noneMatch(item -> item.record().id().equals(staffDay)));

        // The Director's overtime goes to the Owner, who has nobody above and confirms their own.
        authenticateAs(owner, EMPLOYEE);
        attendanceService.approveOvertime(directorDay, noOvertime);
        attendanceService.approveOvertime(ownerDay, noOvertime);
    }

    private LeaveRequestView submitLeave(Person person) {
        authenticateAs(person, EMPLOYEE);
        // Four hours from the start of the shift on a Monday, as the calendar reads it.
        Instant start = calendarService.scheduleFor(person.employeeId(), LEAVE_DAY, LEAVE_DAY).getFirst().startAt();
        LeaveRequestView draft = leaveRequestService.createDraft(new LeaveRequestService.CreateLeaveCommand(
            person.employeeId(), LeaveType.UNPAID, null, start, start.plusSeconds(4 * 3600), 240, "Việc riêng", null));
        return leaveRequestService.submit(draft.id());
    }

    /** A completed day with two hours beyond the payable time. */
    private Long overtimeDay(Person person) {
        return id("""
            INSERT INTO attendance_records(employee_id, work_date, shift_id, scheduled_start_at, scheduled_end_at,
                scheduled_minutes, check_in_at, check_out_at, worked_minutes, payable_minutes, status)
            VALUES (?, '2026-03-03', ?, '2026-03-03T01:00:00Z', '2026-03-03T10:00:00Z', 480,
                '2026-03-03T01:00:00Z', '2026-03-03T12:00:00Z', 600, 480, 'PRESENT') RETURNING id
            """, person.employeeId(), shiftId);
    }

    private Person person(String prefix, Long managerEmployeeId) {
        String suffix = suffix();
        Long employeeId = id("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, ?, '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, "AP" + suffix, "Approval " + prefix);
        jdbc.update("""
            INSERT INTO employee_assignments(employee_id, organization_unit_id, work_location_id, position_id,
                shift_id, manager_employee_id, employment_type, effective_from, created_by_account_id)
            VALUES (?, ?, ?, ?, ?, ?, 'FULL_TIME', '2025-01-01', ?)
            """, employeeId, unitId, locationId, positionId, shiftId, managerEmployeeId, creatorAccountId);
        Long accountId = id("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status) VALUES (?, ?, ?, 'x', 'ACTIVE') RETURNING id
            """, employeeId, "ap_" + prefix + "_" + suffix, "ap_" + prefix + "_" + suffix + "@hrm.local");
        return new Person(employeeId, accountId);
    }

    private void grant(Person person, String roleCode) {
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2025-01-01', ? FROM roles WHERE code = ?
            """, person.accountId(), creatorAccountId, roleCode);
    }

    private Long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void authenticateAs(Person person, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("approval-it")
            .header("alg", "none")
            .claim("accountId", person.accountId())
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
