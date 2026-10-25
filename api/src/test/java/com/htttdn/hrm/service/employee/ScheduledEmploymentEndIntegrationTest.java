package com.htttdn.hrm.service.employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.htttdn.hrm.service.payroll.PayrollDeductionsService;
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

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.exception.BusinessException;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HR ends an employment on a later day: the employee keeps working until the nightly job closes it.
 * Either way nothing payroll-related is dated after the last working day.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=false"
})
@Transactional
class ScheduledEmploymentEndIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmployeeService employeeService;
    @Autowired private ScheduledEmploymentEndJob job;
    @Autowired private EntityManager entityManager;
    @Autowired private EmployeeSalaryHistoryService salaryService;
    @Autowired private PayrollDeductionsService deductionsService;
    @Autowired private EmployeeAssignmentService assignmentService;

    private final LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
    private Long employeeId;
    private Long accountId;
    private Long hrAccountId;
    private Long unitId;
    private Long locationId;
    private Long positionId;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        hrAccountId = id("""
            INSERT INTO accounts(username, email, password_hash, status) VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, "se_hr_" + suffix, "se_hr_" + suffix + "@hrm.local");
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2025-01-01', ? FROM roles WHERE code = 'HR_MANAGER'
            """, hrAccountId, hrAccountId);
        unitId = id("INSERT INTO organization_units(code, name, unit_type) VALUES (?, 'End unit', 'DEPARTMENT') RETURNING id",
            "SEU_" + suffix);
        locationId = id("""
            INSERT INTO work_locations(code, name, location_type, address) VALUES (?, 'End', 'HEAD_OFFICE', 'HN') RETURNING id
            """, "SEL_" + suffix);
        positionId = id("INSERT INTO job_positions(code, title) VALUES (?, 'End role') RETURNING id", "SEP_" + suffix);
        employeeId = id("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Scheduled end', '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, "SE" + suffix);
        jdbc.update("""
            INSERT INTO employee_assignments(employee_id, organization_unit_id, work_location_id, position_id,
                employment_type, effective_from, created_by_account_id)
            VALUES (?, ?, ?, ?, 'FULL_TIME', '2025-01-01', ?)
            """, employeeId, unitId, locationId, positionId, hrAccountId);
        accountId = id("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status) VALUES (?, ?, ?, 'x', 'ACTIVE') RETURNING id
            """, employeeId, "se_emp_" + suffix, "se_emp_" + suffix + "@hrm.local");

        Jwt jwt = Jwt.withTokenValue("scheduled-end-it").header("alg", "none").claim("accountId", hrAccountId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            java.util.List.of("employee.lifecycle.manage", "compensation.manage", "employee.assignment.manage").stream()
                .map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aLaterLastDayKeepsTheEmployeeWorkingUntilTheJobRunsAfterIt() {
        LocalDate lastDay = today.plusDays(3);
        var scheduled = employeeService.endEmployment(employeeId, EmploymentStatus.TERMINATED, lastDay, "Hết hợp đồng");

        assertEquals(EmploymentStatus.ACTIVE, scheduled.employmentStatus());
        assertEquals(EmploymentStatus.TERMINATED, scheduled.scheduledEndStatus());
        assertEquals(lastDay, scheduled.terminationDate());
        assertEquals("ACTIVE", accountStatus());

        // Not due yet: the last working day has not passed.
        job.completeDue();
        assertEquals("ACTIVE", employee().get("employment_status"));

        // The day passes.
        entityManager.flush();
        jdbc.update("UPDATE employees SET termination_date = ? WHERE id = ?", today.minusDays(1), employeeId);
        entityManager.clear();
        job.completeDue();

        Map<String, Object> ended = employee();
        assertEquals("TERMINATED", ended.get("employment_status"));
        assertNull(ended.get("scheduled_end_status"));
        assertNull(ended.get("scheduled_end_by_account_id"));
        assertEquals("DISABLED", accountStatus());
        assertEquals(java.sql.Date.valueOf(today.minusDays(1)), jdbc.queryForObject(
            "SELECT effective_to FROM employee_assignments WHERE employee_id = ?", java.sql.Date.class, employeeId));
    }

    @Test
    void aCancelledScheduleLeavesNoEndBehind() {
        employeeService.endEmployment(employeeId, EmploymentStatus.RESIGNED, today.plusDays(3), "Nghỉ việc");

        var cancelled = employeeService.cancelScheduledEnd(employeeId);

        assertNull(cancelled.scheduledEndStatus());
        assertNull(cancelled.terminationDate());
        Map<String, Object> row = employee();
        assertNull(row.get("termination_date"));
        assertNull(row.get("termination_reason"));
        assertNull(row.get("scheduled_end_status"));
    }

    @Test
    void todayAsTheLastDayEndsAtOnce() {
        var ended = employeeService.endEmployment(employeeId, EmploymentStatus.RESIGNED, today, "Nghỉ việc");

        assertEquals(EmploymentStatus.RESIGNED, ended.employmentStatus());
        assertNull(ended.scheduledEndStatus());
        assertEquals("DISABLED", accountStatus());
    }

    @Test
    void endingClosesTheSalaryInsuranceAndDependentsStillRunningAtTheLastDay() {
        jdbc.update("""
            INSERT INTO employee_salary_history(employee_id, base_salary, effective_from, approved_by_account_id)
            VALUES (?, 20000000, '2025-01-01', ?)
            """, employeeId, hrAccountId);
        jdbc.update("""
            INSERT INTO employee_payroll_profiles(employee_id, effective_from, tax_resident, social_insurance,
                health_insurance, unemployment_insurance, insurance_salary, wage_region, created_by_account_id)
            VALUES (?, '2025-01-01', true, true, true, true, 10000000, 1, ?)
            """, employeeId, hrAccountId);
        jdbc.update("""
            INSERT INTO employee_tax_dependents(employee_id, full_name, effective_from, created_by_account_id)
            VALUES (?, 'Con', '2025-01-01', ?), (?, 'Con sau', ?, ?)
            """, employeeId, hrAccountId, employeeId, today.plusDays(5), hrAccountId);

        employeeService.endEmployment(employeeId, EmploymentStatus.RESIGNED, today, "Nghỉ việc");
        entityManager.flush();

        java.sql.Date lastDay = java.sql.Date.valueOf(today);
        assertEquals(lastDay, jdbc.queryForObject(
            "SELECT effective_to FROM employee_salary_history WHERE employee_id = ?", java.sql.Date.class, employeeId));
        assertEquals(lastDay, jdbc.queryForObject(
            "SELECT effective_to FROM employee_payroll_profiles WHERE employee_id = ?", java.sql.Date.class, employeeId));
        assertEquals(lastDay, jdbc.queryForObject(
            "SELECT effective_to FROM employee_tax_dependents WHERE employee_id = ? AND full_name = 'Con'",
            java.sql.Date.class, employeeId));
        // It never applied, so it is left as entered.
        assertNull(jdbc.queryForObject(
            "SELECT effective_to FROM employee_tax_dependents WHERE employee_id = ? AND full_name = 'Con sau'",
            java.sql.Date.class, employeeId));
    }

    @Test
    void nothingIsDatedAfterTheLastWorkingDay() {
        LocalDate lastDay = today.plusDays(3);
        employeeService.endEmployment(employeeId, EmploymentStatus.RESIGNED, lastDay, "Nghỉ việc");
        LocalDate after = lastDay.plusDays(1);

        assertAfterEnd(() -> salaryService.setSalary(employeeId,
            new EmployeeSalaryHistoryService.SetSalaryCommand(new BigDecimal("21000000"), after, null, null)));
        assertAfterEnd(() -> deductionsService.setProfile(employeeId, new PayrollDeductionsService.ProfileCommand(
            after, true, true, true, true, new BigDecimal("10000000"), (short) 1)));
        assertAfterEnd(() -> deductionsService.addDependent(employeeId,
            new PayrollDeductionsService.DependentCommand("Con", null, after, null)));
        assertAfterEnd(() -> assignmentService.assign(employeeId, new AssignEmployeeRequest(
            unitId, locationId, positionId, null, null, EmploymentType.FULL_TIME, after, null)));

        // Up to the last day is still accepted; an open dependent stops at it.
        assertEquals(lastDay, salaryService.setSalary(employeeId,
            new EmployeeSalaryHistoryService.SetSalaryCommand(new BigDecimal("21000000"), lastDay, null, null)).effectiveFrom());
        assertEquals(lastDay, deductionsService.addDependent(employeeId,
            new PayrollDeductionsService.DependentCommand("Con", null, today.plusDays(1), null)).effectiveTo());
    }

    private static void assertAfterEnd(org.junit.jupiter.api.function.Executable call) {
        BusinessException error = assertThrows(BusinessException.class, call);
        assertEquals(ErrorCode.AFTER_EMPLOYMENT_END, error.getErrorCode());
        assertEquals("effectiveFrom", error.getField());
    }

    private Map<String, Object> employee() {
        entityManager.flush();
        return jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", employeeId);
    }

    private String accountStatus() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, accountId);
    }

    private Long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
