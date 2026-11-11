package com.htttdn.hrm.service.rbac;

import com.htttdn.hrm.config.ClockConfig;
import java.time.Clock;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.role.CreateRoleRequest;
import com.htttdn.hrm.dto.request.role.GrantPermissionRequest;
import com.htttdn.hrm.dto.request.role.ReplaceRolePermissionsRequest;
import com.htttdn.hrm.dto.request.role.UpdateRoleRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private AccountRoleAssignmentCommandService roleAssignmentCommandService;
    @Mock private AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    @Mock private RoleAssignmentPolicy roleAssignmentPolicy;

    @Test
    void replacePermissionsAddsAndRemovesMappingsInOneOperation() {
        Role role = role(false);
        Account actor = Account.builder().id(99L).build();
        Permission retained = permission(1L, "employee.read", PermissionAssignmentPolicy.DELEGABLE);
        Permission added = permission(2L, "employee.update", PermissionAssignmentPolicy.DELEGABLE);
        Permission removed = permission(3L, "employee.delete", PermissionAssignmentPolicy.DELEGABLE);
        RolePermission retainedMapping = mapping(role, retained);
        RolePermission removedMapping = mapping(role, removed);

        when(roleRepository.getRequiredForUpdate(10L)).thenReturn(role);
        when(permissionRepository.findAllById(any())).thenReturn(List.of(added, retained));
        when(currentAccountProvider.account()).thenReturn(actor);
        when(rolePermissionRepository.findByIdRoleId(10L))
            .thenReturn(List.of(retainedMapping, removedMapping));

        var result = service().replacePermissions(
            10L,
            new ReplaceRolePermissionsRequest(List.of(2L, 1L, 2L))
        );

        assertEquals(List.of("employee.read", "employee.update"), result.stream().map(value -> value.code()).toList());
        verify(rolePermissionRepository).deleteAll(List.of(removedMapping));
        verify(rolePermissionRepository).saveAll(argThat(mappings -> {
            List<RolePermission> saved = StreamSupport.stream(mappings.spliterator(), false).toList();
            return saved.size() == 1
                && saved.getFirst().getPermission().getId().equals(2L)
                && saved.getFirst().getCreatedByAccount().getId().equals(99L);
        }));
    }

    @Test
    void replacePermissionsRejectsSystemOnlyPermissionWithoutChangingMappings() {
        Role role = role(false);
        Permission permission = permission(
            4L,
            "organization.company_owner.bootstrap",
            PermissionAssignmentPolicy.SYSTEM_ONLY
        );
        when(roleRepository.getRequiredForUpdate(10L)).thenReturn(role);
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().replacePermissions(
                10L,
                new ReplaceRolePermissionsRequest(List.of(4L))
            )
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("permissionIds", exception.getField());
        verify(rolePermissionRepository, never()).deleteAll(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void replacePermissionsAcceptsEmptyListToClearRole() {
        Role role = role(false);
        Account actor = Account.builder().id(99L).build();
        RolePermission existing = mapping(
            role,
            permission(1L, "employee.read", PermissionAssignmentPolicy.DELEGABLE)
        );
        when(roleRepository.getRequiredForUpdate(10L)).thenReturn(role);
        when(currentAccountProvider.account()).thenReturn(actor);
        when(rolePermissionRepository.findByIdRoleId(10L)).thenReturn(List.of(existing));

        var result = service().replacePermissions(
            10L,
            new ReplaceRolePermissionsRequest(List.of())
        );

        assertEquals(List.of(), result);
        verify(rolePermissionRepository).deleteAll(List.of(existing));
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void replacePermissionsRejectsUnknownPermissionBeforeChangingMappings() {
        when(roleRepository.getRequiredForUpdate(10L)).thenReturn(role(false));
        when(permissionRepository.findAllById(any())).thenReturn(List.of());

        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> service().replacePermissions(
                10L,
                new ReplaceRolePermissionsRequest(List.of(404L))
            )
        );

        assertEquals(ErrorCode.PERMISSION_NOT_FOUND, exception.getErrorCode());
        verify(rolePermissionRepository, never()).deleteAll(any());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    /** No workflow grants or revokes AUTO and SYSTEM_ONLY roles, so a custom role with one would be stuck. */
    @Test
    void customRolesTakeOnlyAGrantPolicyAWorkflowCanUse() {
        when(roleRepository.getRequired(10L)).thenReturn(role(false));

        for (RoleGrantPolicy policy : List.of(RoleGrantPolicy.AUTO, RoleGrantPolicy.SYSTEM_ONLY)) {
            BusinessException created = assertThrows(BusinessException.class,
                () -> service().create(new CreateRoleRequest("CUSTOM_ROLE", "Custom", null, policy)));
            BusinessException updated = assertThrows(BusinessException.class,
                () -> service().update(10L, new UpdateRoleRequest("Custom", null, policy)));

            assertEquals("grantPolicy", created.getField());
            assertEquals("grantPolicy", updated.getField());
        }
        verify(roleRepository, never()).save(any());
    }

    @Test
    void deletingACustomRoleRevokesItsAssignmentsAndPendingRequests() {
        Role role = role(false);
        Account actor = Account.builder().id(99L).build();
        when(roleRepository.getRequired(10L)).thenReturn(role);
        when(currentAccountProvider.account()).thenReturn(actor);

        service().softDelete(10L);

        verify(roleAssignmentCommandService).revokeDeletedRole(role, actor, "Role đã bị xóa");
    }

    @Test
    void grantPermissionRejectsCompanyWidePermissionWhileRoleIsAssignedNarrower() {
        Role role = role(false);
        Permission permission = permission(5L, "payroll.calculate", PermissionAssignmentPolicy.DELEGABLE);
        permission.setCompanyWide(true);
        when(roleRepository.getRequired(10L)).thenReturn(role);
        when(permissionRepository.getRequired(5L)).thenReturn(permission);
        when(accountRoleAssignmentRepository.existsCurrentOrFutureNarrowerThanCompanyByRoleId(eq(10L), any()))
            .thenReturn(true);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().grantPermission(10L, new GrantPermissionRequest(5L))
        );

        assertEquals(ErrorCode.ROLE_SCOPE_CONFLICT, exception.getErrorCode());
        assertEquals("permissionId", exception.getField());
        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    void replacePermissionsRejectsAddingCompanyWidePermissionWhileRoleIsAssignedNarrower() {
        Role role = role(false);
        Permission permission = permission(5L, "payroll.calculate", PermissionAssignmentPolicy.DELEGABLE);
        permission.setCompanyWide(true);
        when(roleRepository.getRequiredForUpdate(10L)).thenReturn(role);
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));
        when(rolePermissionRepository.findByIdRoleId(10L)).thenReturn(List.of());
        when(accountRoleAssignmentRepository.existsCurrentOrFutureNarrowerThanCompanyByRoleId(eq(10L), any()))
            .thenReturn(true);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().replacePermissions(10L, new ReplaceRolePermissionsRequest(List.of(5L)))
        );

        assertEquals(ErrorCode.ROLE_SCOPE_CONFLICT, exception.getErrorCode());
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void replacePermissionsRejectsDroppingEverySelfServicePermissionWhileRoleIsAssignedSelf() {
        Role role = role(false);
        Permission selfService = permission(6L, "payroll.self.read", PermissionAssignmentPolicy.DELEGABLE);
        Permission other = permission(1L, "employee.read", PermissionAssignmentPolicy.DELEGABLE);
        when(roleRepository.getRequiredForUpdate(10L)).thenReturn(role);
        when(permissionRepository.findAllById(any())).thenReturn(List.of(other));
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(99L).build());
        when(rolePermissionRepository.findByIdRoleId(10L)).thenReturn(List.of(mapping(role, selfService)));
        when(accountRoleAssignmentRepository.existsCurrentOrFutureSelfByRoleId(eq(10L), any())).thenReturn(true);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().replacePermissions(10L, new ReplaceRolePermissionsRequest(List.of(1L)))
        );

        assertEquals(ErrorCode.ROLE_SCOPE_CONFLICT, exception.getErrorCode());
        assertEquals("permissionIds", exception.getField());
    }

    @Test
    void replacePermissionsKeepingASelfServicePermissionIgnoresSelfAssignments() {
        Role role = role(false);
        Permission selfService = permission(6L, "payroll.self.read", PermissionAssignmentPolicy.DELEGABLE);
        when(roleRepository.getRequiredForUpdate(10L)).thenReturn(role);
        when(permissionRepository.findAllById(any())).thenReturn(List.of(selfService));
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(99L).build());
        when(rolePermissionRepository.findByIdRoleId(10L)).thenReturn(List.of(mapping(role, selfService)));

        service().replacePermissions(10L, new ReplaceRolePermissionsRequest(List.of(6L)));

        verify(accountRoleAssignmentRepository, never()).existsCurrentOrFutureSelfByRoleId(any(), any());
    }

    @Test
    void revokePermissionRejectsRemovingTheLastSelfServicePermissionWhileRoleIsAssignedSelf() {
        Role role = role(false);
        when(roleRepository.getRequired(10L)).thenReturn(role);
        when(rolePermissionRepository.existsById(new RolePermissionId(10L, 6L))).thenReturn(true);
        when(roleAssignmentPolicy.hasSelfServicePermission(role)).thenReturn(false);
        when(accountRoleAssignmentRepository.existsCurrentOrFutureSelfByRoleId(eq(10L), any())).thenReturn(true);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().revokePermission(10L, 6L)
        );

        assertEquals(ErrorCode.ROLE_SCOPE_CONFLICT, exception.getErrorCode());
        assertEquals("permissionId", exception.getField());
    }

    private Role role(boolean system) {
        return Role.builder().id(10L).code("CUSTOM_ROLE").name("Custom role").isSystem(system).build();
    }

    private Permission permission(
        Long id,
        String code,
        PermissionAssignmentPolicy assignmentPolicy
    ) {
        return Permission.builder()
            .id(id)
            .code(code)
            .name(code)
            .module(PermissionModule.EMPLOYEE)
            .description(code)
            .assignmentPolicy(assignmentPolicy)
            .isActive(true)
            .build();
    }

    private RolePermission mapping(Role role, Permission permission) {
        return RolePermission.builder()
            .id(new RolePermissionId(role.getId(), permission.getId()))
            .role(role)
            .permission(permission)
            .build();
    }

    private RoleService service() {
        return new RoleService(
            roleRepository,
            permissionRepository,
            rolePermissionRepository,
            accountRoleAssignmentRepository,
            currentAccountProvider,
            roleAssignmentCommandService,
            roleAssignmentPolicy,
            Clock.system(ClockConfig.BUSINESS_ZONE)
        );
    }
}
