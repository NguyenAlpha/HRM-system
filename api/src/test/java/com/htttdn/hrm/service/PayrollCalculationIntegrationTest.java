package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

import com.htttdn.hrm.dto.request.payroll.CreatePayrollPeriodRequest;
import com.htttdn.hrm.dto.request.payroll.PayrollActionRequest;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.repository.EmployeeRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs a whole payroll period through the real services and reads the result back from the
 * database. Unit tests mock the repositories, so this is the only test that proves a calculated
 * payslip and its items satisfy the table constraints (NOT NULL snapshots, allowance and net
 * totals) without help from database triggers.
 */
@SpringBootTest
@Transactional
class PayrollCalculationIntegrationTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 1, 31);
    private static final LocalDate TAX_PAYMENT_DATE = LocalDate.of(2026, 2, 5);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PayrollService payrollService;
    @Autowired private AttendanceCalendarService calendarService;
    @Autowired private EmployeeRepository employeeRepository;

    private Long accountantId;
    private Long employeeId;
    private String positionCode;

    @BeforeEach
    void seedPayrollInputs() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        accountantId = jdbc.queryForObject("""
            INSERT INTO accounts(username, email, password_hash, status)
            VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, "payroll_it_" + suffix, "payroll_it_" + suffix + "@hrm.local");
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2026-01-01', ? FROM roles WHERE code = 'PAYROLL_ACCOUNTANT'
            """, accountantId, accountantId);
        authenticateAs(accountantId, "payroll.calculate");

        Long unitId = insertId("""
            INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'Payroll IT', 'DEPARTMENT') RETURNING id
            """, "PIT_U_" + suffix);
        Long locationId = insertId("""
            INSERT INTO work_locations(code, name, location_type, address) VALUES (?, 'Payroll IT', 'HEAD_OFFICE', 'HN') RETURNING id
            """, "PIT_L_" + suffix);
        positionCode = "PIT_P_" + suffix;
        Long positionId = insertId("""
            INSERT INTO job_positions(code, title) VALUES (?, 'Chuyên viên kiểm thử') RETURNING id
            """, positionCode);
        Long shiftId = insertId("""
            INSERT INTO work_shifts(code, name, start_time, end_time, break_minutes, standard_work_minutes)
            VALUES (?, 'Ca hành chính', '08:00', '17:00', 60, 480) RETURNING id
            """, "PIT_S_" + suffix);

        employeeId = insertId("""
            INSERT INTO employees(employee_code, full_name, hire_date, employment_status)
            VALUES (?, 'Payroll IT', '2026-01-01', 'ACTIVE') RETURNING id
            """, "PIT" + suffix);
        jdbc.update("""
            INSERT INTO employee_assignments(employee_id, organization_unit_id, work_location_id, position_id,
                shift_id, employment_type, effective_from, created_by_account_id)
            VALUES (?, ?, ?, ?, ?, 'FULL_TIME', '2026-01-01', ?)
            """, employeeId, unitId, locationId, positionId, shiftId, accountantId);
        jdbc.update("""
            INSERT INTO employee_salary_history(employee_id, base_salary, effective_from, approved_by_account_id)
            VALUES (?, 26000000, '2026-01-01', ?)
            """, employeeId, accountantId);
        jdbc.update("""
            INSERT INTO position_allowance_rules(job_position_id, monthly_amount, effective_from, approved_by_account_id)
            VALUES (?, 1000000, '2026-01-01', ?)
            """, positionId, accountantId);
        jdbc.update("""
            INSERT INTO employee_payroll_profiles(employee_id, effective_from, tax_resident, social_insurance,
                health_insurance, unemployment_insurance, insurance_salary, wage_region, created_by_account_id)
            VALUES (?, '2026-01-01', true, true, true, true, 10000000, 1, ?)
            """, employeeId, accountantId);

        for (AttendanceCalendarService.ScheduledDay day : calendarService.scheduleFor(employeeId, PERIOD_START, PERIOD_END)) {
            jdbc.update("""
                INSERT INTO attendance_records(employee_id, work_date, shift_id, scheduled_start_at, scheduled_end_at,
                    check_in_at, check_out_at, worked_minutes, payable_minutes, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 480, 480, 'PRESENT')
                """, employeeId, day.workDate(), shiftId,
                Timestamp.from(day.startAt()), Timestamp.from(day.endAt()),
                Timestamp.from(day.startAt()), Timestamp.from(day.endAt()));
        }
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void calculatePersistsPayslipAndItemsThatSatisfyTableConstraints() {
        assertEquals(List.of(employeeId),
            employeeRepository.findEmployedDuring(PERIOD_START, PERIOD_END).stream().map(e -> e.getId()).toList(),
            "Payroll calculates every employee employed in January 2026; this test needs a database without"
                + " other employees in that month");

        PayrollPeriodResponse period = payrollService.createPeriod(
            new CreatePayrollPeriodRequest((short) 2026, (short) 1, TAX_PAYMENT_DATE));
        PayrollPeriodResponse calculated = payrollService.calculate(period.id(), new PayrollActionRequest());
        entityManager.flush();

        assertEquals("CALCULATED", calculated.status().name());

        Map<String, Object> payslip = jdbc.queryForMap(
            "SELECT * FROM payslips WHERE payroll_period_id = ?", period.id());
        assertEquals(employeeId, ((Number) payslip.get("employee_id")).longValue());
        assertEquals("Chuyên viên kiểm thử", payslip.get("position_snapshot"));
        assertMoney("26000000.00", payslip.get("base_salary_pay"));
        assertMoney("1000000.00", payslip.get("position_allowance_pay"));
        assertMoney("0.00", payslip.get("seniority_allowance_pay"));
        assertMoney("1000000.00", payslip.get("allowance_pay"));
        assertMoney("27000000.00", payslip.get("gross_pay"));
        // 8% BHXH + 1.5% BHYT + 1% BHTN on the 10,000,000 insurance salary.
        assertMoney("800000.00", payslip.get("employee_social_insurance"));
        assertMoney("150000.00", payslip.get("employee_health_insurance"));
        assertMoney("100000.00", payslip.get("employee_unemployment_insurance"));
        // 27,000,000 - 1,050,000 insurance - 15,500,000 personal deduction.
        assertMoney("10450000.00", payslip.get("taxable_income"));
        // 5% of the first 10,000,000 + 10% of the remaining 450,000.
        assertMoney("545000.00", payslip.get("personal_income_tax"));
        assertMoney("25405000.00", payslip.get("net_pay"));

        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT component_type, component_code, amount FROM payslip_items WHERE payslip_id = ? ORDER BY component_type",
            payslip.get("id"));
        assertEquals(2, items.size());
        assertEquals("BASE_SALARY", items.get(0).get("component_code"));
        assertMoney("26000000.00", items.get(0).get("amount"));
        assertEquals("POSITION_ALLOWANCE_" + positionCode, items.get(1).get("component_code"));
        assertMoney("1000000.00", items.get(1).get("amount"));
    }

    private Long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void authenticateAs(Long accountId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("payroll-it")
            .header("alg", "none")
            .claim("accountId", accountId)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static void assertMoney(String expected, Object actual) {
        assertEquals(new BigDecimal(expected), actual);
    }
}
