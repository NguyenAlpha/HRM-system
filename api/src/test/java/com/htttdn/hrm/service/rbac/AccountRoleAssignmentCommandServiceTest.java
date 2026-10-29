package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.service.auth.RefreshTokenService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRoleAssignmentCommandServiceTest {

    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-24T05:00:00Z"), ClockConfig.BUSINESS_ZONE);

    @Mock private AccountRoleAssignmentRepository roleAssignmentRepository;
    @Mock private RefreshTokenService refreshTokenService;

    @Test
    void revokingHeldRolesEndsTheBusinessRolesStillRunningOnTheEndDate() {
        Account actor = Account.builder().id(9L).build();
        LocalDate endDate = LocalDate.of(2026, 10, 20);
        AccountRoleAssignment current = roleAssignment(RoleGrantPolicy.HR_ASSIGNABLE, LocalDate.of(2025, 1, 1), null);
        AccountRoleAssignment director = roleAssignment(RoleGrantPolicy.OWNER_APPROVAL, LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1));
        AccountRoleAssignment future = roleAssignment(RoleGrantPolicy.HR_ASSIGNABLE, LocalDate.of(2026, 12, 1), null);
        AccountRoleAssignment ended = roleAssignment(RoleGrantPolicy.HR_ASSIGNABLE, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 31));
        AccountRoleAssignment automatic = roleAssignment(RoleGrantPolicy.AUTO, LocalDate.of(2025, 1, 1), null);
        AccountRoleAssignment systemOnly = roleAssignment(RoleGrantPolicy.SYSTEM_ONLY, LocalDate.of(2025, 1, 1), null);
        when(roleAssignmentRepository.findByAccountIdAndRevokedAtIsNull(5L))
            .thenReturn(List.of(current, director, future, ended, automatic, systemOnly));

        commandService().revokeHeldRoles(5L, endDate, actor, " Kết thúc làm việc: Nghỉ việc ");

        assertEquals(endDate, current.getEffectiveTo());
        assertEquals(actor, current.getRevokedByAccount());
        assertNotNull(current.getRevokedAt());
        assertEquals("Kết thúc làm việc: Nghỉ việc", current.getRevocationReason());
        assertEquals(endDate, director.getEffectiveTo());
        assertNotNull(director.getRevokedAt());
        // A role that had not started yet is revoked but keeps its own dates.
        assertNotNull(future.getRevokedAt());
        assertNull(future.getEffectiveTo());
        assertNull(ended.getRevokedAt());
        assertNull(automatic.getRevokedAt());
        assertNull(systemOnly.getRevokedAt());
    }

    private AccountRoleAssignmentCommandService commandService() {
        return new AccountRoleAssignmentCommandService(
            null, null, roleAssignmentRepository, null, null, null, refreshTokenService, TODAY
        );
    }

    private AccountRoleAssignment roleAssignment(RoleGrantPolicy policy, LocalDate from, LocalDate to) {
        AccountRoleAssignment assignment = new AccountRoleAssignment();
        assignment.setRole(Role.builder().grantPolicy(policy).build());
        assignment.setEffectiveFrom(from);
        assignment.setEffectiveTo(to);
        return assignment;
    }
}
