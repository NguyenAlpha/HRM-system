package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.AccountPermissionOverride;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void appliesOverridesPerAssignmentBeforeCombiningPermissions() {
        Role employeeRole = Role.builder().id(1L).code("EMPLOYEE").build();
        Role managerRole = Role.builder().id(2L).code("BRANCH_MANAGER").build();
        AccountRoleAssignment employeeAssignment = AccountRoleAssignment.builder()
            .id(11L)
            .role(employeeRole)
            .build();
        AccountRoleAssignment managerAssignment = AccountRoleAssignment.builder()
            .id(12L)
            .role(managerRole)
            .build();
        Permission read = Permission.builder().id(21L).code("employee.read").build();
        Permission manage = Permission.builder().id(22L).code("employee.manage").build();

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
        assertEquals(List.of("employee.manage", "employee.read"), snapshot.permissions());
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
        return AccountPermissionOverride.builder()
            .id(id)
            .accountRoleAssignment(assignment)
            .permission(permission)
            .effect(effect)
            .createdAt(Instant.now().plusMillis(id))
            .build();
    }

    private AccountAuthorizationService service() {
        return new AccountAuthorizationService(
            assignmentRepository,
            rolePermissionRepository,
            overrideRepository
        );
    }
}
