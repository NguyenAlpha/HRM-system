package com.htttdn.hrm.service.account;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AccountSearchCriteria;
import com.htttdn.hrm.dto.response.account.AccountResponse;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deleting an account is soft: it is disabled for good and leaves the list, its business roles are
 * revoked, and its employee may get a new account. Runs on an empty or seeded database inside a
 * rolled-back transaction.
 */
@SpringBootTest
@Transactional
class AccountDeletionIntegrationTest {

    private static final String[] ADMIN = { "account.read", "account.manage" };

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private AccountAdminService accountAdminService;
    @Autowired private AccountRepository accountRepository;

    private String marker;
    private Long adminId;
    private Long employeeId;

    @BeforeEach
    void seed() {
        marker = "del" + UUID.randomUUID().toString().substring(0, 8);
        adminId = account(marker + "_admin", null, "ACTIVE");
        employeeId = jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Mời nhầm', '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, Long.class, marker.toUpperCase());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDeletedAccountLeavesTheListAndItsEmployeeMayGetANewOne() {
        Long pendingId = account(marker + "_typo", employeeId, "PENDING");
        authenticateAs(adminId);

        AccountResponse deleted = accountAdminService.delete(pendingId, "  Gõ sai email  ");
        entityManager.flush();

        assertEquals(AccountStatus.DISABLED, deleted.status());
        assertNotNull(deleted.deletedAt());
        assertEquals("Gõ sai email", deleted.deletionReason());
        assertEquals(List.of(marker + "_admin"), usernames(false));
        assertEquals(List.of(marker + "_typo"), usernames(true));
        assertThrows(ConflictException.class, () -> accountAdminService.activate(pendingId));
        assertThrows(ConflictException.class, () -> accountAdminService.delete(pendingId, "Lần nữa"));

        // The employee is free for a new account; the deleted one stays as history.
        Long replacementId = account(marker + "_right", employeeId, "PENDING");
        entityManager.clear();
        assertEquals(replacementId, accountRepository.findByEmployeeId(employeeId).orElseThrow().getId());
        assertEquals(List.of(marker + "_admin", marker + "_right"), usernames(false));
    }

    @Test
    void deletingRevokesSessionsOfAnActiveAccountAndItsBusinessRoles() {
        Long activeId = account(marker + "_hr", employeeId, "ACTIVE");
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2025-01-01', ? FROM roles WHERE code = 'HR_MANAGER'
            """, activeId, adminId);
        authenticateAs(adminId);

        accountAdminService.delete(activeId, "Tài khoản trùng");
        entityManager.flush();

        assertEquals(0, jdbc.queryForObject("""
            SELECT count(*) FROM account_role_assignments ra JOIN roles r ON r.id = ra.role_id
            WHERE ra.account_id = ? AND r.code = 'HR_MANAGER' AND ra.revoked_at IS NULL
            """, Integer.class, activeId));
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE account_id = ? AND revoked_at IS NULL", Integer.class, activeId));
    }

    @Test
    void nobodyDeletesTheirOwnAccountOrACompanyOwner() {
        Long ownerId = account(marker + "_owner", null, "ACTIVE");
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2025-01-01', ? FROM roles WHERE code = 'COMPANY_OWNER'
            """, ownerId, adminId);
        authenticateAs(adminId);

        assertThrows(ConflictException.class, () -> accountAdminService.delete(adminId, "Tự xóa"));
        assertThrows(ForbiddenException.class, () -> accountAdminService.delete(ownerId, "Xóa chủ sở hữu"));
    }

    private List<String> usernames(boolean deleted) {
        return accountAdminService.list(new AccountSearchCriteria(marker, null, null, deleted), PageRequest.of(0, 20, Sort.by("username")))
            .map(AccountResponse::username).getContent();
    }

    private Long account(String username, Long employee, String status) {
        return jdbc.queryForObject("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status)
            VALUES (?, ?, ?, ?, ?) RETURNING id
            """, Long.class, employee, username, username + "@hrm.local", "PENDING".equals(status) ? null : "x", status);
    }

    private void authenticateAs(Long accountId) {
        Jwt jwt = Jwt.withTokenValue("delete-it").header("alg", "none").claim("accountId", accountId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(ADMIN).map(SimpleGrantedAuthority::new).toList()));
    }
}
