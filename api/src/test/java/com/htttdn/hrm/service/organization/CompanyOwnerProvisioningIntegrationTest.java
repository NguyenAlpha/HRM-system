package com.htttdn.hrm.service.organization;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.CompanyProfileRequest;
import com.htttdn.hrm.dto.request.organization.CreateCompanyOwnerAccountRequest;
import com.htttdn.hrm.dto.request.organization.CreateCompanyOwnerEmployeeRequest;
import com.htttdn.hrm.dto.request.organization.CreateOrganizationCompanyOwnerRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.ConflictException;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The system admin hands the company over once: the owner's employee record, a PENDING account with an
 * activation token, and COMPANY_OWNER plus DIRECTOR. Revokes the seeded owner and director inside a
 * rolled-back transaction, so it runs on an empty or seeded database.
 */
@SpringBootTest
@Transactional
class CompanyOwnerProvisioningIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;
    @Autowired private OrganizationCompanyOwnerProvisioningService provisioningService;

    private Long adminId;

    @BeforeEach
    void seed() {
        String username = "own_admin_" + UUID.randomUUID().toString().substring(0, 8);
        adminId = jdbc.queryForObject("""
            INSERT INTO accounts(username, email, password_hash, status) VALUES (?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, username, username + "@hrm.local");
        // Start from a company that has no owner and no director yet.
        jdbc.update("""
            UPDATE account_role_assignments SET revoked_at = now(), revoked_by_account_id = ?, revocation_reason = 'test'
            WHERE revoked_at IS NULL AND role_id IN (SELECT id FROM roles WHERE code IN ('COMPANY_OWNER', 'DIRECTOR'))
            """, adminId);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theFirstOwnerIsCreatedPendingWithOwnerAndDirectorRoles() {
        authenticate("organization.company_owner.bootstrap");

        var response = provisioningService.provision(request());
        entityManager.flush();

        assertEquals("PENDING", response.accountProvisioning().account().status().name());
        assertNotNull(response.accountProvisioning().invitation().activationToken());
        assertEquals("COMPANY_OWNER", response.companyOwnerRoleAssignment().roleCode());
        assertEquals("DIRECTOR", response.directorRoleAssignment().roleCode());
        assertEquals(response.accountProvisioning().account().id(), response.directorRoleAssignment().accountId());
        assertEquals("ACTIVE", jdbc.queryForObject(
            "SELECT employment_status FROM employees WHERE id = ?", String.class, response.employeeId()));
    }

    @Test
    void aSecondOwnerIsRefused() {
        authenticate("organization.company_owner.bootstrap");
        provisioningService.provision(request());

        ConflictException exception = assertThrows(ConflictException.class,
            () -> provisioningService.provision(request()));

        assertEquals(ErrorCode.COMPANY_OWNER_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void onlyTheBootstrapPermissionProvisions() {
        authenticate("organization.manage");

        assertThrows(AccessDeniedException.class, () -> provisioningService.provision(request()));
    }

    private CreateOrganizationCompanyOwnerRequest request() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now(clock);
        return new CreateOrganizationCompanyOwnerRequest(
            new CompanyProfileRequest("ACME", "Công ty ACME", null, null, null, null),
            new CreateCompanyOwnerEmployeeRequest("Chủ Sở Hữu", "own" + marker + "@acme.vn", null, today),
            new CreateCompanyOwnerAccountRequest("owner_" + marker),
            today,
            "Chủ sở hữu công ty",
            "Kiêm Giám đốc đầu tiên"
        );
    }

    private void authenticate(String authority) {
        Jwt jwt = Jwt.withTokenValue("owner-provisioning-it").header("alg", "none").claim("accountId", adminId).build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
    }
}
