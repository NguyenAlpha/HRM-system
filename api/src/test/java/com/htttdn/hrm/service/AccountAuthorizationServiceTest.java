package com.htttdn.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.response.account.AccountPermissionGrantResponse;
import com.htttdn.hrm.dto.response.account.PermissionGrantSource;
import com.htttdn.hrm.entity.AccountPermissionOverride;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAuthorizationServiceTest {

    @Mock
    private AccountRoleAssignmentRepository assignmentRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private AccountPermissionOverrideRepository overrideRepository;

    @Test
    void readsAssignmentsActiveOnTheVietnamDateWhileUtcIsStillTheDayBefore() {
        // 23:30 UTC on 31 October is 06:30 on 1 November in Vietnam.
        Clock clock = Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ClockConfig.BUSINESS_ZONE);
        when(assignmentRepository.findActiveWithRoleByAccountId(eq(7L), any())).thenReturn(List.of());

        service(clock).getScopes(7L, "employee.list.read");

        verify(assignmentRepository).findActiveWithRoleByAccountId(7L, LocalDate.of(2026, 11, 1));
    }

    @Test
    void appliesOverridesPerAssignmentBeforeCombiningPermissions() {
        Role employeeRole = Role.builder().id(1L).code("EMPLOYEE").name("Employee").build();
        Role managerRole = Role.builder().id(2L).code("BRANCH_MANAGER").name("Branch Manager").build();
        AccountRoleAssignment employeeAssignment = AccountRoleAssignment.builder()
            .id(11L)
            .role(employeeRole)
            .scopeType(RoleScopeType.SELF)
            .build();
        AccountRoleAssignment managerAssignment = AccountRoleAssignment.builder()
            .id(12L)
            .role(managerRole)
            .scopeType(RoleScopeType.COMPANY)
            .build();
        Permission read = Permission.builder().id(21L).code("employee.read")
            .name("View employees").module(com.htttdn.hrm.entity.enums.PermissionModule.EMPLOYEE).build();
        Permission manage = Permission.builder().id(22L).code("employee.update")
            .name("Update employees").module(com.htttdn.hrm.entity.enums.PermissionModule.EMPLOYEE).build();

        when(assignmentRepository.findActiveWithRoleByAccountId(eq(7L), any()))
            .thenReturn(List.of(employeeAssignment, managerAssignment));
        when(rolePermissionRepository.findActiveByRoleIds(List.of(1L, 2L))).thenReturn(List.of(
            rolePermission(employeeRole, read),
            rolePermission(managerRole, read)
        ));
        when(overrideRepository.findActiveByAssignmentIds(eq(List.of(11L, 12L)), any()))
            .thenReturn(List.of(
                permissionOverride(31L, employeeAssignment, read, PermissionOverrideEffect.REVOKE),
                permissionOverride(32L, managerAssignment, manage, PermissionOverrideEffect.GRANT)
            ));

        var snapshot = service().getSnapshot(7L);

        assertEquals(List.of("BRANCH_MANAGER", "EMPLOYEE"), snapshot.roles());
        assertEquals(List.of("employee.read", "employee.update"), snapshot.permissions());
        assertEquals("Branch Manager", snapshot.roleDetails().getFirst().name());
        assertEquals("View employees", snapshot.permissionDetails().getFirst().name());
    }

    @Test
    void returnsOnlyScopesWhoseAssignmentEffectivelyGrantsPermission() {
        Role employeeRole = Role.builder().id(1L).code("EMPLOYEE").name("Employee").build();
        Role managerRole = Role.builder().id(2L).code("BRANCH_MANAGER").name("Branch Manager").build();
        AccountRoleAssignment revokedSelfAssignment = AccountRoleAssignment.builder()
            .id(11L)
            .role(employeeRole)
            .scopeType(RoleScopeType.SELF)
            .build();
        AccountRoleAssignment branchAssignment = AccountRoleAssignment.builder()
            .id(12L)
            .role(managerRole)
            .scopeType(RoleScopeType.LOCATION)
            .workLocation(WorkLocation.builder().id(50L).name("Head Office").build())
            .build();
        Permission read = Permission.builder().id(21L).code("employee.read")
            .name("View employees").module(com.htttdn.hrm.entity.enums.PermissionModule.EMPLOYEE).build();

        when(assignmentRepository.findActiveWithRoleByAccountId(eq(7L), any()))
            .thenReturn(List.of(revokedSelfAssignment, branchAssignment));
        when(rolePermissionRepository.findActiveByRoleIds(List.of(1L, 2L))).thenReturn(List.of(
            rolePermission(employeeRole, read),
            rolePermission(managerRole, read)
        ));
        when(overrideRepository.findActiveByAssignmentIds(eq(List.of(11L, 12L)), any()))
            .thenReturn(List.of(
                permissionOverride(31L, revokedSelfAssignment, read, PermissionOverrideEffect.REVOKE)
            ));

        var scopes = service().getScopes(7L, "employee.read");

        assertEquals(1, scopes.size());
        assertEquals(RoleScopeType.LOCATION, scopes.getFirst().scopeType());
        assertEquals(50L, scopes.getFirst().workLocationId());
    }

    @Test
    void listsWhichAssignmentGrantsEachPermissionAndWhetherAnOverrideDoes() {
        Role employeeRole = Role.builder().id(1L).code("EMPLOYEE").name("Employee").build();
        Role managerRole = Role.builder().id(2L).code("BRANCH_MANAGER").name("Branch Manager").build();
        AccountRoleAssignment employeeAssignment = AccountRoleAssignment.builder()
            .id(11L)
            .role(employeeRole)
            .scopeType(RoleScopeType.SELF)
            .build();
        AccountRoleAssignment managerAssignment = AccountRoleAssignment.builder()
            .id(12L)
            .role(managerRole)
            .scopeType(RoleScopeType.COMPANY)
            .build();
        Permission read = Permission.builder().id(21L).code("employee.read")
            .name("View employees").module(com.htttdn.hrm.entity.enums.PermissionModule.EMPLOYEE).build();
        Permission manage = Permission.builder().id(22L).code("employee.update")
            .name("Update employees").module(com.htttdn.hrm.entity.enums.PermissionModule.EMPLOYEE).build();

        when(assignmentRepository.findActiveWithRoleByAccountId(eq(7L), any()))
            .thenReturn(List.of(employeeAssignment, managerAssignment));
        when(rolePermissionRepository.findActiveByRoleIds(List.of(1L, 2L))).thenReturn(List.of(
            rolePermission(employeeRole, read),
            rolePermission(managerRole, read)
        ));
        when(overrideRepository.findActiveByAssignmentIds(eq(List.of(11L, 12L)), any()))
            .thenReturn(List.of(
                permissionOverride(31L, employeeAssignment, read, PermissionOverrideEffect.REVOKE),
                permissionOverride(32L, managerAssignment, manage, PermissionOverrideEffect.GRANT)
            ));

        var grants = service().getPermissionGrants(7L);

        assertEquals(List.of("employee.read", "employee.update"),
            grants.stream().map(AccountPermissionGrantResponse::code).toList());
        assertEquals(List.of(new PermissionGrantSource(12L, "BRANCH_MANAGER", "Branch Manager", false)),
            grants.get(0).grantedBy());
        assertEquals(List.of(new PermissionGrantSource(12L, "BRANCH_MANAGER", "Branch Manager", true)),
            grants.get(1).grantedBy());
        assertEquals("Update employees", grants.get(1).name());
    }

    private RolePermission rolePermission(Role role, Permission permission) {
        return RolePermission.builder()
            .id(new RolePermissionId(role.getId(), permission.getId()))
            .role(role)
            .permission(permission)
            .build();
    }

    private AccountPermissionOverride permissionOverride(
        Long id,
        AccountRoleAssignment assignment,
        Permission permission,
        PermissionOverrideEffect effect
    ) {
        AccountPermissionOverride override = AccountPermissionOverride.builder()
            .id(id)
            .accountRoleAssignment(assignment)
            .permission(permission)
            .effect(effect)
            .build();
        // Overrides apply oldest first; auditing sets createdAt only when saved, so set it here.
        ReflectionTestUtils.setField(override, "createdAt", Instant.now().plusMillis(id));
        return override;
    }

    private AccountAuthorizationService service() {
        return service(Clock.system(ClockConfig.BUSINESS_ZONE));
    }

    private AccountAuthorizationService service(Clock clock) {
        return new AccountAuthorizationService(
            assignmentRepository,
            rolePermissionRepository,
            overrideRepository,
            clock
        );
    }
}
