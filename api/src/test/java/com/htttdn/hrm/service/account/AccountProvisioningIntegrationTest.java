package com.htttdn.hrm.service.account;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.repository.AccountRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A new account gets the base EMPLOYEE role from the day the employee starts work. Runs on an empty or
 * seeded database inside a rolled-back transaction.
 */
@SpringBootTest
@Transactional
class AccountProvisioningIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountProvisioningService provisioningService;
    @Autowired private Clock clock;

    @Test
    void theEmployeeRoleStartsOnTheHireDateWhenItIsLater() {
        LocalDate hireDate = LocalDate.now(clock).plusDays(10);

        LocalDate effectiveFrom = provisionedRoleStart(hireDate);

        // Before the first working day the person has no employee access yet.
        assertEquals(hireDate, effectiveFrom);
    }

    @Test
    void theEmployeeRoleStartsTodayForSomeoneAlreadyWorking() {
        assertEquals(LocalDate.now(clock), provisionedRoleStart(LocalDate.of(2025, 1, 1)));
    }

    private LocalDate provisionedRoleStart(LocalDate hireDate) {
        String marker = "prv" + UUID.randomUUID().toString().substring(0, 8);
        Long employeeId = jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status, work_email)
            VALUES (?, 'Người mới', ?, ?, 'PROBATION', ?) RETURNING id
            """, Long.class, marker.toUpperCase(), hireDate, hireDate, marker + "@hrm.local");
        Account actor = accountRepository.save(Account.builder()
            .username(marker + "_hr").email(marker + "_hr@hrm.local").passwordHash("x")
            .status(AccountStatus.ACTIVE).failedLoginCount(0).build());

        Long accountId = provisioningService.provisionPendingAccount(employeeId, marker, actor).account().id();
        entityManager.flush();

        return jdbc.queryForObject("""
            SELECT ra.effective_from FROM account_role_assignments ra JOIN roles r ON r.id = ra.role_id
            WHERE ra.account_id = ? AND r.code = 'EMPLOYEE'
            """, LocalDate.class, accountId);
    }
}
