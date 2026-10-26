package com.htttdn.hrm.service.leave;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.htttdn.hrm.service.attendance.AttendanceCalendarService;
import com.htttdn.hrm.service.attendance.AttendanceQueryService;
import com.htttdn.hrm.service.attendance.OvertimeService;
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
import com.htttdn.hrm.dto.response.attendance.TimesheetRowResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.AppException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.service.leave.LeaveRequestService.LeaveRequestView;

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
    @Autowired private LeaveRequestQueryService leaveRequestQueryService;
    @Autowired private OvertimeService overtimeService;
    @Autowired private AttendanceQueryService attendanceQueryService;
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
        // The payroll seed approves every past month, which freezes its attendance; this rolled-back
        // transaction cancels the period holding the test day so leave and overtime can still change it.
        jdbc.update("UPDATE payroll_periods SET status = 'CANCELLED' WHERE ? BETWEEN period_start AND period_end", LEAVE_DAY);
        String suffix = suffix();
        unitId = id("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Approval unit', 'DEPARTMENT') RETURNING id",
            "APU_" + suffix);
        locationId = id("""
            INSERT INTO work_locations(code, name, location_type, address) VALUES (?, 'Approval', 'HEAD_OFFICE', 'HN') RETURNING id
            """, "APL_" + suffix);
        positionId = id("INSERT INTO job_positions(code, title) VALUES (?, 'Approval role') RETURNING id", "APP_" + suffix);
        // Saved through JPA: a TIME column written over raw JDBC reads back shifted.
        shiftId = workShiftRepository.saveAndFlush(WorkShift.builder()
            .code("APS_" + suffix).name("Ca hành chính")
            .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
            .breakMinutes(60).breakStartTime(LocalTime.of(12, 0))
            .standardWorkMinutes(480).graceLateMinutes(0).crossesMidnight(false).isActive(true)
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
        assertEquals(pending.id(), leaveRequestQueryService.listPending(org.springframework.data.domain.Pageable.unpaged())
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
        assertThrows(ForbiddenException.class, () -> overtimeService.approveOvertime(staffDay, noOvertime));
        authenticateAs(staff, EMPLOYEE);
        assertThrows(ForbiddenException.class, () -> overtimeService.approveOvertime(staffDay, noOvertime));
        authenticateAs(director, EMPLOYEE);
        assertThrows(ForbiddenException.class, () -> overtimeService.approveOvertime(directorDay, noOvertime));

        authenticateAs(manager, EMPLOYEE);
        assertEquals(staffDay, overtimeService.listOvertimeAwaitingDecision().stream()
            .map(OvertimeApprovalItemResponse::record).filter(record -> record.id().equals(staffDay))
            .findFirst().orElseThrow().id());
        overtimeService.approveOvertime(staffDay, noOvertime);
        assertTrue(overtimeService.listOvertimeAwaitingDecision().stream()
            .noneMatch(item -> item.record().id().equals(staffDay)));

        // The Director's overtime goes to the Owner, who has nobody above and confirms their own.
        authenticateAs(owner, EMPLOYEE);
        overtimeService.approveOvertime(directorDay, noOvertime);
        overtimeService.approveOvertime(ownerDay, noOvertime);
    }

    @Test
    void leaveListShowsSubmittedRequestsInsideTheReadersScopeOnly() {
        LeaveRequestView pending = submitLeave(staff);
        authenticateAs(staff, EMPLOYEE);
        Instant start = calendarService.scheduleFor(staff.employeeId(), LEAVE_DAY.plusDays(1), LEAVE_DAY.plusDays(1))
            .getFirst().startAt();
        leaveRequestService.createDraft(new LeaveRequestService.CreateLeaveCommand(
            staff.employeeId(), LeaveType.UNPAID, null, start, start.plusSeconds(4 * 3600), "Nháp", null));

        Long otherUnitId = id("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Other unit', 'DEPARTMENT') RETURNING id",
            "APO_" + suffix());
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, organization_unit_id, effective_from, granted_by_account_id)
            SELECT ?, id, 'ORG_UNIT', ?, '2025-01-01', ? FROM roles WHERE code = 'HR_MANAGER'
            """, hr.accountId(), unitId, creatorAccountId);
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, organization_unit_id, effective_from, granted_by_account_id)
            SELECT ?, id, 'ORG_UNIT', ?, '2025-01-01', ? FROM roles WHERE code = 'HR_MANAGER'
            """, manager.accountId(), otherUnitId, creatorAccountId);

        authenticateAs(hr, "request.read");
        // The draft is the employee's own and stays hidden.
        assertEquals(List.of(pending.id()), searchIds(null, null, null));
        assertEquals(List.of(pending.id()), searchIds("approval staff", LeaveRequestStatus.PENDING, LEAVE_DAY));
        assertEquals(List.of(), searchIds(null, LeaveRequestStatus.APPROVED, null));
        assertEquals(List.of(), searchIds(null, null, LEAVE_DAY.plusDays(1)));

        authenticateAs(manager, "request.read");
        assertEquals(List.of(), searchIds(null, null, null));
    }

    @Test
    void annualLeaveTakesTheScheduledMinutesOfEveryDayItCovers() {
        authenticateAs(staff, EMPLOYEE);
        // Monday 13:00 to Wednesday 12:00 (UTC+7): an afternoon, a whole day and a morning.
        LeaveRequestView draft = leaveRequestService.createDraft(new LeaveRequestService.CreateLeaveCommand(
            staff.employeeId(), LeaveType.ANNUAL, null,
            Instant.parse("2026-03-02T06:00:00Z"), Instant.parse("2026-03-04T05:00:00Z"), "Về quê", null));
        assertEquals(240 + 480 + 240, draft.requestedMinutes());

        leaveRequestService.submit(draft.id());
        authenticateAs(manager, EMPLOYEE);
        leaveRequestService.approve(draft.id(), null);

        // Every day of the period is on leave, not only the first one.
        assertEquals(List.of(240, 480, 240), jdbc.queryForList("""
            SELECT leave_minutes FROM attendance_records WHERE leave_request_id = ? ORDER BY work_date
            """, Integer.class, draft.id()));
    }

    @Test
    void annualLeaveBeyondTheBalanceIsRejectedWhenTheDraftIsCreated() {
        authenticateAs(staff, EMPLOYEE);
        // Three weeks hold 18 work days; the year grants 12.
        AppException thrown = assertThrows(AppException.class, () -> leaveRequestService.createDraft(
            new LeaveRequestService.CreateLeaveCommand(staff.employeeId(), LeaveType.ANNUAL, null,
                Instant.parse("2026-03-02T01:00:00Z"), Instant.parse("2026-03-21T10:00:00Z"), "Du lịch", null)));

        assertEquals(ErrorCode.INSUFFICIENT_LEAVE_BALANCE, thrown.getErrorCode());
    }

    @Test
    void leaveOnADayOffIsRejected() {
        authenticateAs(staff, EMPLOYEE);
        // 2026-03-08 is a Sunday.
        AppException thrown = assertThrows(AppException.class, () -> leaveRequestService.createDraft(
            new LeaveRequestService.CreateLeaveCommand(staff.employeeId(), LeaveType.UNPAID, null,
                Instant.parse("2026-03-08T01:00:00Z"), Instant.parse("2026-03-08T10:00:00Z"), "Việc riêng", null)));

        assertEquals(ErrorCode.LEAVE_OUTSIDE_WORK_SCHEDULE, thrown.getErrorCode());
    }

    @Test
    void hrCannotCreateSubmitOrCancelAnotherEmployeesLeave() {
        authenticateAs(staff, EMPLOYEE);
        Instant start = calendarService.scheduleFor(staff.employeeId(), LEAVE_DAY, LEAVE_DAY)
            .getFirst().startAt();
        LeaveRequestView draft = leaveRequestService.createDraft(new LeaveRequestService.CreateLeaveCommand(
            staff.employeeId(), LeaveType.UNPAID, null, start, start.plusSeconds(4 * 3600),
            "Personal leave", null));

        authenticateAs(hr, "request.self.create", "request.self.cancel", "request.manage");
        assertThrows(ForbiddenException.class, () -> leaveRequestService.createDraft(
            new LeaveRequestService.CreateLeaveCommand(staff.employeeId(), LeaveType.UNPAID,
                null, start, start.plusSeconds(4 * 3600), "On behalf", null)));
        assertThrows(ForbiddenException.class, () -> leaveRequestService.submit(draft.id()));
        assertThrows(ForbiddenException.class, () -> leaveRequestService.cancel(draft.id()));
        assertEquals("DRAFT", jdbc.queryForObject(
            "SELECT status FROM leave_requests WHERE id = ?", String.class, draft.id()));
    }

    @Test
    void timesheetListsEveryEmployeeInTheReadersScopeWithTheirRecordsOfTheMonth() {
        Long staffDay = overtimeDay(staff);
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, organization_unit_id, effective_from, granted_by_account_id)
            SELECT ?, id, 'ORG_UNIT', ?, '2025-01-01', ? FROM roles WHERE code = 'HR_MANAGER'
            """, hr.accountId(), unitId, creatorAccountId);
        authenticateAs(hr, "attendance.read");

        List<TimesheetRowResponse> rows = attendanceQueryService.timesheet(2026, 3, null, null);
        assertEquals(Set.of(owner.employeeId(), director.employeeId(), manager.employeeId(), staff.employeeId(), hr.employeeId()),
            rows.stream().map(TimesheetRowResponse::employeeId).collect(Collectors.toSet()));
        assertEquals(List.of(staffDay), rows.stream().filter(row -> row.employeeId().equals(staff.employeeId()))
            .findFirst().orElseThrow().records().stream().map(record -> record.id()).toList());

        assertEquals(List.of(staff.employeeId()),
            attendanceQueryService.timesheet(2026, 3, "approval staff", null).stream().map(TimesheetRowResponse::employeeId).toList());
        assertTrue(attendanceQueryService.timesheet(2026, 4, "approval staff", null).getFirst().records().isEmpty());
        // Hired in 2025-01: not on the timesheet of an earlier month.
        assertTrue(attendanceQueryService.timesheet(2024, 12, null, null).isEmpty());
    }

    private List<Long> searchIds(String keyword, LeaveRequestStatus status, LocalDate day) {
        return leaveRequestQueryService.search(
                new LeaveRequestQueryService.LeaveRequestSearch(keyword, null, status, null, day, day),
                org.springframework.data.domain.Pageable.unpaged())
            .map(LeaveRequestView::id).getContent();
    }

    private LeaveRequestView submitLeave(Person person) {
        authenticateAs(person, EMPLOYEE);
        // Four hours from the start of the shift on a Monday, as the calendar reads it.
        Instant start = calendarService.scheduleFor(person.employeeId(), LEAVE_DAY, LEAVE_DAY).getFirst().startAt();
        LeaveRequestView draft = leaveRequestService.createDraft(new LeaveRequestService.CreateLeaveCommand(
            person.employeeId(), LeaveType.UNPAID, null, start, start.plusSeconds(4 * 3600), "Việc riêng", null));
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
