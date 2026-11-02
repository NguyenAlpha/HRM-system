package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.request.account.CreatePermissionOverrideRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.account.CompanyOwnerAccountGuard;
import com.htttdn.hrm.service.auth.RefreshTokenService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPermissionOverrideServiceTest {

    @Mock private AccountRoleAssignmentRepository assignmentRepository;
    @Mock private AccountPermissionOverrideRepository overrideRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private CompanyOwnerAccountGuard companyOwnerAccountGuard;

    private final Permission companyWide = permission(5L, "payroll.calculate", true);
    private final Permission perEmployee = permission(6L, "employee.update", false);

    @Test
    void companyWidePermissionCannotBeGrantedOnAUnitScopedAssignment() {
        AccountRoleAssignment assignment = assignment(RoleScopeType.ORG_UNIT);
        when(currentAccountProvider.accountId()).thenReturn(1L);
        when(assignmentRepository.getRequiredForUpdate(30L, 2L)).thenReturn(assignment);
        when(permissionRepository.getRequired(5L)).thenReturn(companyWide);

        ConflictException exception = assertThrows(ConflictException.class, () -> service().create(2L, 30L,
            new CreatePermissionOverrideRequest(5L, PermissionOverrideEffect.GRANT, LocalDate.of(2026, 11, 1), null,
                "Hỗ trợ tính lương")));

        assertEquals(ErrorCode.PERMISSION_OVERRIDE_NOT_ALLOWED, exception.getErrorCode());
        verify(overrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void unitScopedAssignmentIsNotOfferedCompanyWidePermissions() {
        when(assignmentRepository.getRequired(30L, 2L)).thenReturn(assignment(RoleScopeType.ORG_UNIT));
        when(permissionRepository.findByIsActiveTrueAndAssignmentPolicyOrderByModuleAscCodeAsc(
            PermissionAssignmentPolicy.DELEGABLE)).thenReturn(List.of(companyWide, perEmployee));

        var options = service().availablePermissions(2L, 30L);

        assertEquals(List.of("employee.update"), options.stream().map(option -> option.permissionCode()).toList());
    }

    private AccountPermissionOverrideService service() {
        return new AccountPermissionOverrideService(assignmentRepository, overrideRepository, permissionRepository,
            rolePermissionRepository, currentAccountProvider, refreshTokenService, companyOwnerAccountGuard,
            Clock.system(ClockConfig.BUSINESS_ZONE));
    }

    private static AccountRoleAssignment assignment(RoleScopeType scopeType) {
        return AccountRoleAssignment.builder()
            .id(30L)
            .account(Account.builder().id(2L).status(AccountStatus.ACTIVE).build())
            .role(Role.builder().id(10L).code("CUSTOM_ROLE").build())
            .scopeType(scopeType)
            .effectiveFrom(LocalDate.of(2026, 1, 1))
            .build();
    }

    private static Permission permission(Long id, String code, boolean companyWide) {
        return Permission.builder()
            .id(id)
            .code(code)
            .name(code)
            .module(PermissionModule.PAYROLL)
            .description(code)
            .assignmentPolicy(PermissionAssignmentPolicy.DELEGABLE)
            .companyWide(companyWide)
            .isActive(true)
            .build();
    }
}
