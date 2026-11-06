package com.htttdn.hrm.service.rbac;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
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

import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentOptionResponse;
import com.htttdn.hrm.entity.enums.RoleScopeType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The roles the "request a role" form offers: only HR_ASSIGNABLE and OWNER_APPROVAL roles not deleted, and
 * a role with a company-wide permission only at COMPANY scope. Runs on an empty or seeded database inside a
 * rolled-back transaction.
 */
@SpringBootTest
@Transactional
class RoleAssignmentOptionIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RoleAssignmentOptionService optionService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyRequestableRolesAreOfferedWithTheirScopes() {
        String marker = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        role("HR_" + marker, "HR_ASSIGNABLE", false);
        Long ownerApproved = role("OA_" + marker, "OWNER_APPROVAL", false);
        role("SYS_" + marker, "SYSTEM_ONLY", false);
        role("DEL_" + marker, "HR_ASSIGNABLE", true);
        // A permission that only makes sense company-wide pins the role to COMPANY scope.
        Long companyWide = jdbc.queryForObject("""
            INSERT INTO permissions(code, name, module, description, assignment_policy, company_wide)
            VALUES (?, 'Test', 'PAYROLL', 'Test', 'DELEGABLE', true) RETURNING id
            """, Long.class, "test.company_wide." + marker.toLowerCase());
        jdbc.update("INSERT INTO role_permissions(role_id, permission_id) VALUES (?, ?)", ownerApproved, companyWide);
        authenticate("role.assignment.request");

        Map<String, RoleAssignmentOptionResponse> offered = optionService.listAvailableRoles().stream()
            .filter(option -> option.roleCode().endsWith("_" + marker))
            .collect(Collectors.toMap(RoleAssignmentOptionResponse::roleCode, Function.identity()));

        assertEquals(List.of("HR_" + marker, "OA_" + marker), offered.keySet().stream().sorted().toList());
        assertFalse(offered.get("HR_" + marker).requiresApproval());
        assertEquals(List.of(RoleScopeType.values()), offered.get("HR_" + marker).allowedScopeTypes());
        assertTrue(offered.get("OA_" + marker).requiresApproval());
        assertEquals(List.of(RoleScopeType.COMPANY), offered.get("OA_" + marker).allowedScopeTypes());
    }

    @Test
    void theListNeedsTheRequestPermission() {
        authenticate("role.read");

        assertThrows(AccessDeniedException.class, () -> optionService.listAvailableRoles());
    }

    private Long role(String code, String grantPolicy, boolean deleted) {
        return jdbc.queryForObject("""
            INSERT INTO roles(code, name, grant_policy, deleted_at) VALUES (?, ?, ?, ?) RETURNING id
            """, Long.class, code, "Role " + code, grantPolicy, deleted ? Timestamp.valueOf("2026-01-01 00:00:00") : null);
    }

    private void authenticate(String authority) {
        Jwt jwt = Jwt.withTokenValue("role-option-it").header("alg", "none").claim("accountId", 1L).build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
    }
}
