package com.htttdn.hrm.service;

import java.time.LocalDate;
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

import com.htttdn.hrm.dto.request.account.CreatePermissionOverrideRequest;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.exception.ForbiddenException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An account-management administrator (the seeded SYSTEM_ADMIN permission set) manages ordinary
 * accounts but cannot take over a Company Owner account, and nobody overrides their own permissions.
 * Uses its own Company Owner inside a rolled-back transaction, so it runs on an empty or seeded database.
 */
@SpringBootTest
@Transactional
class CompanyOwnerAccountGuardIntegrationTest {

    private static final String[] ADMIN_AUTHORITIES = {
        "account.read", "account.manage", "account.activation.manage",
        "account.role.assign", "account.permission.override.manage",
    };

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountAdminService accountAdminService;
    @Autowired private AccountPermissionOverrideService overrideService;

    private Long adminId;
    private Long ownerId;
    private Long ownerAssignmentId;
    private Long staffId;

    @BeforeEach
    void seedAccounts() {
        adminId = insertAccount("guard_admin");
        ownerId = insertAccount("guard_owner");
        staffId = insertAccount("guard_staff");
        ownerAssignmentId = jdbc.queryForObject("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2026-01-01', ? FROM roles WHERE code = 'COMPANY_OWNER'
            RETURNING id
            """, Long.class, ownerId, adminId);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorManagesOrdinaryAccountsButNotCompanyOwner() {
        authenticateAs(adminId, ADMIN_AUTHORITIES);

        assertEquals("DISABLED", accountAdminService.suspend(staffId).status().name());
        assertThrows(ForbiddenException.class, () -> accountAdminService.suspend(ownerId));
        assertThrows(ForbiddenException.class, () -> accountAdminService.initiatePasswordReset(ownerId));
        assertThrows(ForbiddenException.class, () -> overrideService.create(ownerId, ownerAssignmentId, revokeOverride()));
    }

    @Test
    void companyOwnerAccountCanBeManagedByAnotherCompanyOwner() {
        Long secondOwnerId = insertAccount("guard_owner2");
        jdbc.update("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            SELECT ?, id, 'COMPANY', '2026-01-01', ? FROM roles WHERE code = 'COMPANY_OWNER'
            """, secondOwnerId, adminId);
        authenticateAs(secondOwnerId, ADMIN_AUTHORITIES);

        assertEquals("DISABLED", accountAdminService.suspend(ownerId).status().name());
    }

    @Test
    void nobodyOverridesTheirOwnPermissions() {
        authenticateAs(ownerId, ADMIN_AUTHORITIES);

        assertThrows(ForbiddenException.class, () -> overrideService.create(ownerId, ownerAssignmentId, revokeOverride()));
    }

    private CreatePermissionOverrideRequest revokeOverride() {
        Long permissionId = jdbc.queryForObject(
            "SELECT id FROM permissions WHERE code = 'account.read'", Long.class);
        return new CreatePermissionOverrideRequest(
            permissionId, PermissionOverrideEffect.REVOKE, LocalDate.now(), null, "guard test");
    }

    private Long insertAccount(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
            INSERT INTO accounts(username, email, password_hash, status)
            VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, prefix + "_" + suffix, prefix + "_" + suffix + "@hrm.local");
    }

    private void authenticateAs(Long accountId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("guard-it")
            .header("alg", "none")
            .claim("accountId", accountId)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
