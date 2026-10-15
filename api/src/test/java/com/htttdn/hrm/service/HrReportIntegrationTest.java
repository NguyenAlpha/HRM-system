package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.util.List;
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

import com.htttdn.hrm.dto.response.report.MonthlyHrStatusReportResponse;
import com.htttdn.hrm.dto.response.report.TurnoverReportResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HR reports for a reader scoped to one unit: people who left are still counted, people of other
 * units are not. Builds its own unit inside a rolled-back transaction, so it runs on an
 * empty or seeded database.
 */
@SpringBootTest
@Transactional
class HrReportIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReportService reportService;
    @Autowired private LeaveEntitlementService leaveEntitlementService;

    private Long unitId;
    private Long locationId;
    private Long positionId;
    private Long creatorAccountId;
    private Long stayingId;
    private Long leavingId;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        unitId = id("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Turnover unit', 'DEPARTMENT') RETURNING id",
            "TOU_" + suffix);
        Long otherUnitId = id("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Other', 'DEPARTMENT') RETURNING id",
            "TOO_" + suffix);
        locationId = id("INSERT INTO work_locations(code, name, location_type, address) VALUES (?, 'T', 'HEAD_OFFICE', 'HN') RETURNING id",
            "TOL_" + suffix);
        positionId = id("INSERT INTO job_positions(code, title) VALUES (?, 'Turnover role') RETURNING id", "TOP_" + suffix);
        creatorAccountId = id("INSERT INTO accounts(username, email, password_hash, status) VALUES (?, ?, 'x', 'ACTIVE') RETURNING id",
            "to_creator_" + suffix, "to_creator_" + suffix + "@hrm.local");

        employee(unitId, "2024-01-01", "2025-02-28", "RESIGNED");
        stayingId = employee(unitId, "2025-01-01", null, "ACTIVE");
        leavingId = employee(unitId, "2025-03-10", "2025-06-15", "TERMINATED");
        employee(otherUnitId, "2025-05-01", "2025-07-01", "RESIGNED");

        Long readerAccountId = id("INSERT INTO accounts(username, email, password_hash, status) VALUES (?, ?, 'x', 'ACTIVE') RETURNING id",
            "to_reader_" + suffix, "to_reader_" + suffix + "@hrm.local");
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, organization_unit_id, effective_from, granted_by_account_id)
            SELECT ?, id, 'ORG_UNIT', ?, '2020-01-01', ? FROM roles WHERE code = 'HR_MANAGER'
            """, readerAccountId, unitId, creatorAccountId);
        Jwt jwt = Jwt.withTokenValue("turnover-it").header("alg", "none").claim("accountId", readerAccountId).build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("report.hr.read"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void countsHiresAndLeaversOfTheReadersUnitMonthByMonth() {
        TurnoverReportResponse report = reportService.getTurnoverReport(2025);

        assertEquals(12, report.months().size());
        // Start of each month = end of the month before; leavers drop out on their last day.
        assertEquals(List.of(1L, 2L, 1L, 2L, 2L, 2L), report.months().subList(0, 6).stream()
            .map(TurnoverReportResponse.TurnoverMonth::headcountStart).toList());
        TurnoverReportResponse.TurnoverMonth february = report.months().get(1);
        assertEquals(2, february.headcountStart());
        assertEquals(1, february.terminations());
        assertEquals(1, february.headcountEnd());
        assertEquals(new BigDecimal("66.67"), february.turnoverRate());
        assertEquals(1, report.months().get(5).terminations());
        assertEquals(1, report.months().get(5).headcountEnd());

        assertEquals(1, report.headcountStart());
        assertEquals(1, report.headcountEnd());
        assertEquals(2, report.hires());
        assertEquals(2, report.terminations());
        assertEquals(List.of("HIRE", "TERMINATION", "HIRE", "TERMINATION"),
            report.events().stream().map(TurnoverReportResponse.TurnoverEvent::type).toList());
    }

    @Test
    void leaveBalancesListEveryoneOfTheUnitWhoWorkedDuringTheYear() {
        List<LeaveEntitlementService.EmployeeBalanceView> balances = leaveEntitlementService.balancesForReport(2025, null, null);

        // The 2024 hire who left in February still worked in 2025; the other unit's employee is outside the scope.
        assertEquals(3, balances.size());
        assertEquals(List.of(12 * 480, 12 * 480, 10 * 480), balances.stream()
            .sorted(java.util.Comparator.comparing(row -> row.balance().employeeId()))
            .map(row -> row.balance().grantedMinutes()).toList());
        assertEquals(0, leaveEntitlementService.balancesForReport(2023, null, null).size());
    }

    @Test
    void theMonthlyStatusListsEachPersonWithTheirLeaveAndAbsences() {
        Long shiftId = id("""
            INSERT INTO work_shifts(code, name, start_time, end_time, break_minutes, break_start_time, standard_work_minutes)
            VALUES (?, 'Ca hành chính', '08:00', '17:00', 60, '12:00', 480) RETURNING id
            """, "TOS_" + UUID.randomUUID().toString().substring(0, 8));
        Long annualId = leave(stayingId, "ANNUAL", "2025-06-02 08:00+07", "2025-06-03 12:00+07", 720);
        attendance(stayingId, shiftId, "2025-06-02", annualId, 480, "PAID_LEAVE");
        attendance(stayingId, shiftId, "2025-06-03", annualId, 240, "PRESENT");
        Long sickId = leave(stayingId, "SICK", "2025-06-10 08:00+07", "2025-06-10 17:00+07", 480);
        attendance(stayingId, shiftId, "2025-06-10", sickId, 480, "SICK_LEAVE");
        attendance(leavingId, shiftId, "2025-06-04", null, 0, "UNAUTHORIZED_ABSENCE");

        MonthlyHrStatusReportResponse report = reportService.getMonthlyStatusReport(2025, 6);

        assertEquals(2, report.headcountStart());
        assertEquals(0, report.hires());
        assertEquals(1, report.terminations());
        assertEquals(1, report.headcountEnd());
        assertEquals(1, report.employeesOnLeave());
        assertEquals(new BigDecimal("2.50"), report.leaveDays());
        assertEquals(1, report.absentDays());
        // The other unit's employee and the one who left in February are not listed.
        assertEquals(List.of(stayingId, leavingId), report.rows().stream()
            .sorted(java.util.Comparator.comparing(MonthlyHrStatusReportResponse.Row::employeeId))
            .map(MonthlyHrStatusReportResponse.Row::employeeId).toList());
        MonthlyHrStatusReportResponse.Row staying = row(report, stayingId);
        assertEquals("ACTIVE", staying.status());
        assertEquals("Turnover unit", staying.organizationUnitName());
        assertEquals(new BigDecimal("1.50"), staying.annualLeaveDays());
        assertEquals(new BigDecimal("1.00"), staying.sickLeaveDays());
        MonthlyHrStatusReportResponse.Row leaving = row(report, leavingId);
        assertEquals("TERMINATED", leaving.status());
        assertEquals("Turnover unit", leaving.organizationUnitName());
        assertEquals(1, leaving.absentDays());

        assertEquals("NEW_HIRE", row(reportService.getMonthlyStatusReport(2025, 3), leavingId).status());
    }

    private static MonthlyHrStatusReportResponse.Row row(MonthlyHrStatusReportResponse report, Long employeeId) {
        return report.rows().stream().filter(row -> row.employeeId().equals(employeeId)).findFirst().orElseThrow();
    }

    private Long leave(Long employeeId, String type, String start, String end, int minutes) {
        return id("""
            INSERT INTO leave_requests(employee_id, leave_type, salary_treatment, start_at, end_at, requested_minutes, reason, status)
            VALUES (?, ?, 'EMPLOYER_PAID', ?::timestamptz, ?::timestamptz, ?, 'Nghỉ', 'APPROVED') RETURNING id
            """, employeeId, type, start, end, minutes);
    }

    private void attendance(Long employeeId, Long shiftId, String date, Long leaveRequestId, int leaveMinutes, String status) {
        jdbc.update("""
            INSERT INTO attendance_records(employee_id, work_date, shift_id, leave_request_id, scheduled_start_at,
                scheduled_end_at, scheduled_minutes, leave_minutes, status)
            VALUES (?, ?::date, ?, ?, (?::date + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
                (?::date + time '17:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 480, ?, ?)
            """, employeeId, date, shiftId, leaveRequestId, date, date, leaveMinutes, status);
    }

    private Long employee(Long unit, String hired, String ended, String status) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long employeeId = id("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status, termination_date)
            VALUES (?, ?, ?::date, ?::date, ?, ?::date) RETURNING id
            """, "TO" + suffix, "Turnover " + suffix, hired, hired, status, ended);
        jdbc.update("""
            INSERT INTO employee_assignments(employee_id, organization_unit_id, work_location_id, position_id,
                employment_type, effective_from, effective_to, created_by_account_id)
            VALUES (?, ?, ?, ?, 'FULL_TIME', ?::date, ?::date, ?)
            """, employeeId, unit, locationId, positionId, hired, ended, creatorAccountId);
        return employeeId;
    }

    private Long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
