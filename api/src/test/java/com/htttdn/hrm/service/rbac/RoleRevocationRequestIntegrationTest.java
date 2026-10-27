package com.htttdn.hrm.service.rbac;

import java.time.LocalDate;
import java.util.Arrays;
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

import com.htttdn.hrm.dto.request.roleassignment.ApproveRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleAssignmentRequest;
import com.htttdn.hrm.dto.request.roleassignment.CreateRoleRevocationRequest;
import com.htttdn.hrm.dto.request.roleassignment.RejectRoleAssignmentRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentRequestResponse;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestType;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HR asks to take back a role through the same workflow as a grant: an HR_ASSIGNABLE role is revoked
 * at once, an OWNER_APPROVAL role waits for the Company Owner. Builds its own roles, employee and
 * accounts inside a rolled-back transaction, so it runs on an empty or seeded database.
 */
@SpringBootTest
@Transactional
class RoleRevocationRequestIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RoleAssignmentRequestService requestService;

    private Long requesterId;
    private Long approverId;
    private Long targetAccountId;

    @BeforeEach
    void seed() {
        approverId = insertAccount("rev_owner", null);
        requesterId = insertAccount("rev_hr", null);
        Long requesterRoleId = insertRole("OWNER_APPROVAL");
        jdbc.update("""
            INSERT INTO role_permissions(role_id, permission_id)
            SELECT ?, id FROM permissions WHERE code = 'role.assignment.request'
            """, requesterRoleId);
        insertAssignment(requesterId, requesterRoleId);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long employeeId = jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Revocation Target', '2025-01-01', '2025-01-01', 'ACTIVE') RETURNING id
            """, Long.class, "REV" + suffix);
        targetAccountId = insertAccount("rev_target", employeeId);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsOnlyAssignmentsARevocationCanTarget() {
        Long ownerApprovalId = insertAssignment(targetAccountId, insertRole("OWNER_APPROVAL"));
        insertAssignment(targetAccountId, insertRole("AUTO"));
        Long endedId = insertAssignment(targetAccountId, insertRole("HR_ASSIGNABLE"));
        jdbc.update("UPDATE account_role_assignments SET effective_to = '2026-01-31' WHERE id = ?", endedId);
        authenticateAs(requesterId, "role.assignment.request");

        assertEquals(List.of(ownerApprovalId), requestService.listRevocableAssignments(targetAccountId).stream()
            .map(AccountRoleAssignmentResponse::id)
            .toList());
    }

    @Test
    void hrAssignableRoleIsRevokedAtOnce() {
        Long assignmentId = insertAssignment(targetAccountId, insertRole("HR_ASSIGNABLE"));
        authenticateAs(requesterId, "role.assignment.request");

        RoleAssignmentRequestResponse response = requestService.createRevocation(
            new CreateRoleRevocationRequest(assignmentId, "Chuyển công tác"));

        assertEquals(RoleAssignmentRequestType.REVOKE, response.requestType());
        assertEquals(RoleAssignmentRequestStatus.APPROVED, response.status());
        assertNull(response.assignment());
        assertEquals(assignmentId, response.targetAssignment().id());
        assertNotNull(response.targetAssignment().revokedAt());
        assertEquals("Chuyển công tác", response.targetAssignment().revocationReason());
        assertEquals(requesterId, response.targetAssignment().revokedByAccountId());
    }

    @Test
    void ownerApprovalRoleIsRevokedOnlyWhenTheOwnerApproves() {
        Long assignmentId = insertAssignment(targetAccountId, insertRole("OWNER_APPROVAL"));
        authenticateAs(requesterId, "role.assignment.request");

        RoleAssignmentRequestResponse pending = requestService.createRevocation(
            new CreateRoleRevocationRequest(assignmentId, "Thôi kiêm nhiệm"));

        assertEquals(RoleAssignmentRequestStatus.PENDING, pending.status());
        assertNull(pending.targetAssignment().revokedAt());

        authenticateAs(approverId, "role.assignment.approve");
        RoleAssignmentRequestResponse approved = requestService.approve(
            pending.id(), new ApproveRoleAssignmentRequest(null));

        assertEquals(RoleAssignmentRequestStatus.APPROVED, approved.status());
        assertNotNull(approved.targetAssignment().revokedAt());
        assertEquals(approverId, approved.targetAssignment().revokedByAccountId());
        assertEquals("Thôi kiêm nhiệm", approved.targetAssignment().revocationReason());
    }

    @Test
    void rejectedRevocationLeavesTheRoleInPlace() {
        Long assignmentId = insertAssignment(targetAccountId, insertRole("OWNER_APPROVAL"));
        authenticateAs(requesterId, "role.assignment.request");
        RoleAssignmentRequestResponse pending = requestService.createRevocation(
            new CreateRoleRevocationRequest(assignmentId, "Thôi kiêm nhiệm"));

        authenticateAs(approverId, "role.assignment.approve");
        RoleAssignmentRequestResponse rejected = requestService.reject(
            pending.id(), new RejectRoleAssignmentRequest("Vẫn cần role này"));

        assertEquals(RoleAssignmentRequestStatus.REJECTED, rejected.status());
        assertNull(rejected.targetAssignment().revokedAt());
    }

    @Test
    void rolesManagedElsewhereOrAlreadyGoneCannotBeRequested() {
        Long automaticId = insertAssignment(targetAccountId, insertRole("AUTO"));
        Long revokedId = insertAssignment(targetAccountId, insertRole("HR_ASSIGNABLE"));
        jdbc.update("""
            UPDATE account_role_assignments
            SET revoked_at = now(), revoked_by_account_id = ?, revocation_reason = 'done'
            WHERE id = ?
            """, approverId, revokedId);
        authenticateAs(requesterId, "role.assignment.request");

        ConflictException automatic = assertThrows(ConflictException.class, () -> requestService.createRevocation(
            new CreateRoleRevocationRequest(automaticId, "x")));
        ConflictException revoked = assertThrows(ConflictException.class, () -> requestService.createRevocation(
            new CreateRoleRevocationRequest(revokedId, "x")));

        assertEquals(ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED, automatic.getErrorCode());
        assertEquals(ErrorCode.ROLE_ASSIGNMENT_NOT_ALLOWED, revoked.getErrorCode());
    }

    @Test
    void onlyOnePendingRevocationPerAssignment() {
        Long assignmentId = insertAssignment(targetAccountId, insertRole("OWNER_APPROVAL"));
        authenticateAs(requesterId, "role.assignment.request");
        requestService.createRevocation(new CreateRoleRevocationRequest(assignmentId, "Lần một"));

        // The unique index aborts the transaction, so this has to be the last statement of the test.
        ConflictException duplicate = assertThrows(ConflictException.class, () -> requestService.createRevocation(
            new CreateRoleRevocationRequest(assignmentId, "Lần hai")));

        assertEquals(ErrorCode.ROLE_ASSIGNMENT_REQUEST_EXISTS, duplicate.getErrorCode());
    }

    /** Like a revocation, a grant request cannot touch a Company Owner account unless the requester is one. */
    @Test
    void grantRequestForACompanyOwnerNeedsACompanyOwner() {
        Long ownerRoleId = jdbc.queryForObject("""
            INSERT INTO roles(code, name, grant_policy, is_system) VALUES ('COMPANY_OWNER', 'Company Owner', 'SYSTEM_ONLY', true)
            ON CONFLICT (code) DO UPDATE SET code = EXCLUDED.code RETURNING id
            """, Long.class);
        insertAssignment(targetAccountId, ownerRoleId);
        Long roleId = insertRole("HR_ASSIGNABLE");
        String roleCode = jdbc.queryForObject("SELECT code FROM roles WHERE id = ?", String.class, roleId);
        authenticateAs(requesterId, "role.assignment.request");

        assertThrows(ForbiddenException.class, () -> requestService.create(new CreateRoleAssignmentRequest(
            targetAccountId, roleCode, RoleScopeType.COMPANY, null, null, LocalDate.of(2026, 1, 1), null, "x")));
    }

    private Long insertRole(String grantPolicy) {
        String code = "IT_REV_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return jdbc.queryForObject("""
            INSERT INTO roles(code, name, grant_policy) VALUES (?, ?, ?) RETURNING id
            """, Long.class, code, code, grantPolicy);
    }

    private Long insertAssignment(Long accountId, Long roleId) {
        return jdbc.queryForObject("""
            INSERT INTO account_role_assignments(account_id, role_id, scope_type, effective_from, granted_by_account_id)
            VALUES (?, ?, 'COMPANY', '2026-01-01', ?) RETURNING id
            """, Long.class, accountId, roleId, approverId);
    }

    private Long insertAccount(String prefix, Long employeeId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
            INSERT INTO accounts(employee_id, username, email, password_hash, status)
            VALUES (?, ?, ?, 'x', 'ACTIVE') RETURNING id
            """, Long.class, employeeId, prefix + "_" + suffix, prefix + "_" + suffix + "@hrm.local");
    }

    private void authenticateAs(Long accountId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("revocation-it")
            .header("alg", "none")
            .claim("accountId", accountId)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
