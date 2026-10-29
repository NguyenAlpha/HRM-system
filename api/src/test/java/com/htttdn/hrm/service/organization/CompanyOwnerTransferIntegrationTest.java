package com.htttdn.hrm.service.organization;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.TransferCompanyOwnerRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Company Owner hands ownership to another active employee: it takes effect at once, only
 * COMPANY_OWNER moves and the old owner keeps their other roles. Revokes the seeded owner inside a
 * rolled-back transaction, so it runs on an empty or seeded database.
 */
@SpringBootTest
@Transactional
class CompanyOwnerTransferIntegrationTest {

    private static final String PASSWORD = "Owner@123";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;
    @Autowired private CompanyOwnerTransferService transferService;

    private Long ownerId;
    private Long recipientId;

    @BeforeEach
    void seed() {
        ownerId = account("owner", employee(), "ACTIVE");
        recipientId = account("heir", employee(), "ACTIVE");
        // Only this test's Company Owner counts.
        jdbc.update("""
            UPDATE account_role_assignments SET revoked_at = now(), revoked_by_account_id = ?, revocation_reason = 'test'
            WHERE revoked_at IS NULL AND role_id IN (SELECT id FROM roles WHERE code = 'COMPANY_OWNER')
            """, ownerId);
        assign(ownerId, "COMPANY_OWNER");
        assign(ownerId, "DIRECTOR");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownershipMovesAtOnceAndTheOldOwnerKeepsTheirOtherRoles() {
        authenticateAs(ownerId, "ROLE_COMPANY_OWNER");

        var assignment = transferService.transfer(new TransferCompanyOwnerRequest(recipientId, PASSWORD, " Nghỉ hưu "));
        entityManager.flush();

        LocalDate today = LocalDate.now(clock);
        assertEquals(recipientId, assignment.accountId());
        assertEquals(today, assignment.effectiveFrom());
        assertEquals(1, heldCount(recipientId, "COMPANY_OWNER"));
        assertEquals(0, heldCount(ownerId, "COMPANY_OWNER"));
        assertEquals(today, jdbc.queryForObject("""
            SELECT ra.effective_to FROM account_role_assignments ra JOIN roles r ON r.id = ra.role_id
            WHERE ra.account_id = ? AND r.code = 'COMPANY_OWNER'
            """, LocalDate.class, ownerId));
        assertEquals("Nghỉ hưu", jdbc.queryForObject("""
            SELECT ra.revocation_reason FROM account_role_assignments ra JOIN roles r ON r.id = ra.role_id
            WHERE ra.account_id = ? AND r.code = 'COMPANY_OWNER'
            """, String.class, ownerId));
        assertEquals(1, heldCount(ownerId, "DIRECTOR"));
    }

    @Test
    void aWrongPasswordChangesNothing() {
        authenticateAs(ownerId, "ROLE_COMPANY_OWNER");

        BusinessException exception = assertThrows(BusinessException.class,
            () -> transferService.transfer(new TransferCompanyOwnerRequest(recipientId, "wrong", "x")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        assertEquals("password", exception.getField());
        assertEquals(1, heldCount(ownerId, "COMPANY_OWNER"));
    }

    @Test
    void theRecipientMustBeAnotherActiveAccountOfAnActiveEmployee() {
        authenticateAs(ownerId, "ROLE_COMPANY_OWNER");
        Long pendingId = account("pending", employee(), "PENDING");
        Long noEmployeeId = account("system", null, "ACTIVE");
        Long leavingId = account("leaving", employee(), "ACTIVE");
        jdbc.update("""
            UPDATE employees SET termination_date = '2099-12-31', termination_reason = 'x', scheduled_end_status = 'RESIGNED',
                scheduled_end_by_account_id = ?
            WHERE id = (SELECT employee_id FROM accounts WHERE id = ?)
            """, ownerId, leavingId);
        // A resignation still open would end the new owner's employment, which is refused once they own.
        Long resigningId = account("resigning", employee(), "ACTIVE");
        Long askingId = account("asking", employee(), "ACTIVE");
        resignation(resigningId, "APPROVED");
        resignation(askingId, "PENDING");

        for (Long recipient : new Long[] { pendingId, noEmployeeId, leavingId, resigningId, askingId }) {
            ConflictException exception = assertThrows(ConflictException.class,
                () -> transferService.transfer(new TransferCompanyOwnerRequest(recipient, PASSWORD, "x")));
            assertEquals(ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED, exception.getErrorCode());
        }
        BusinessException self = assertThrows(BusinessException.class,
            () -> transferService.transfer(new TransferCompanyOwnerRequest(ownerId, PASSWORD, "x")));
        assertEquals("accountId", self.getField());
        assertEquals(1, heldCount(ownerId, "COMPANY_OWNER"));
    }

    @Test
    void onlyTheCurrentCompanyOwnerTransfers() {
        authenticateAs(recipientId, "employee.read");
        assertThrows(AccessDeniedException.class,
            () -> transferService.transfer(new TransferCompanyOwnerRequest(recipientId, PASSWORD, "x")));

        // A token that still says COMPANY_OWNER after ownership moved is not enough.
        authenticateAs(recipientId, "ROLE_COMPANY_OWNER");
        assertThrows(ForbiddenException.class,
            () -> transferService.transfer(new TransferCompanyOwnerRequest(ownerId, PASSWORD, "x")));
    }

    private int heldCount(Long accountId, String roleCode) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM account_role_assignments ra JOIN roles r ON r.id = ra.role_id
            WHERE ra.account_id = ? AND r.code = ? AND ra.revoked_at IS NULL
            """, Integer.class, accountId, roleCode);
    }

    private void assign(Long accountId, String roleCode) {
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2025-01-01', ? FROM roles WHERE code = ?
            """, accountId, accountId, roleCode);
    }

    private void resignation(Long accountId, String status) {
        jdbc.update("""
            INSERT INTO resignation_requests(employee_id, last_working_date, reason, status, approver_employee_id)
            SELECT employee_id, '2099-12-31', 'Nghỉ', ?, employee_id FROM accounts WHERE id = ?
            """, status, accountId);
    }

    private Long employee() {
        return jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Transfer Person', '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, Long.class, "TRF" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private Long account(String prefix, Long employeeId, String status) {
        String username = "trf_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status)
            VALUES (?, ?, ?, ?, ?) RETURNING id
            """, Long.class, employeeId, username, username + "@hrm.local",
            "PENDING".equals(status) ? null : passwordEncoder.encode(PASSWORD), status);
    }

    private void authenticateAs(Long accountId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("transfer-it").header("alg", "none").claim("accountId", accountId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
