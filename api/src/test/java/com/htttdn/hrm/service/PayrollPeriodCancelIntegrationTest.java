package com.htttdn.hrm.service;

import java.time.LocalDate;
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

import com.htttdn.hrm.dto.request.payroll.CreatePayrollPeriodRequest;
import com.htttdn.hrm.dto.response.payroll.PayrollPeriodResponse;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.PayrollPeriodRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A cancelled period must not block its month: the year/month uniqueness only covers live periods,
 * and lookups by date must ignore the cancelled row. Runs against the real database because the
 * rule lives in a partial unique index.
 */
@SpringBootTest
@Transactional
class PayrollPeriodCancelIntegrationTest {

    // A month far from any real or seeded payroll data.
    private static final short YEAR = 2091;
    private static final short MONTH = 3;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PayrollService payrollService;
    @Autowired private PayrollPeriodRepository payrollPeriodRepository;

    @BeforeEach
    void authenticateAccountant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long accountId = jdbc.queryForObject("""
            INSERT INTO accounts(username, email, password_hash, status)
            VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, "payroll_cancel_" + suffix, "payroll_cancel_" + suffix + "@hrm.local");
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2026-01-01', ? FROM roles WHERE code = 'PAYROLL_ACCOUNTANT'
            """, accountId, accountId);
        Jwt jwt = Jwt.withTokenValue("payroll-cancel-it")
            .header("alg", "none")
            .claim("accountId", accountId)
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("payroll.calculate"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cancelledPeriodFreesItsMonthForANewPeriod() {
        PayrollPeriodResponse cancelled = payrollService.createPeriod(new CreatePayrollPeriodRequest(YEAR, MONTH));
        payrollService.cancel(cancelled.id());
        entityManager.flush();

        PayrollPeriodResponse recreated = payrollService.createPeriod(new CreatePayrollPeriodRequest(YEAR, MONTH));
        entityManager.flush();

        assertNotEquals(cancelled.id(), recreated.id());
        assertEquals(recreated.id(),
            payrollPeriodRepository.findContainingDateForUpdate(LocalDate.of(YEAR, MONTH, 10)).orElseThrow().getId(),
            "Date lookups used by attendance and holidays must skip the cancelled period");
        assertThrows(ConflictException.class,
            () -> payrollService.createPeriod(new CreatePayrollPeriodRequest(YEAR, MONTH)),
            "A live period still blocks a second one for the same month");
    }
}
