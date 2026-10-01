package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.role.ReplaceRolePermissionsRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.service.impl.RoleServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private AccountRepository accountRepository;

    @Test
    void replacePermissionsAddsAndRemovesMappingsInOneOperation() {
        Role role = role(false);
        Account actor = Account.builder().id(99L).build();
        Permission retained = permission(1L, "employee.read", PermissionAssignmentPolicy.DELEGABLE);
        Permission added = permission(2L, "employee.update", PermissionAssignmentPolicy.DELEGABLE);
        Permission removed = permission(3L, "employee.delete", PermissionAssignmentPolicy.DELEGABLE);
        RolePermission retainedMapping = mapping(role, retained);
        RolePermission removedMapping = mapping(role, removed);

        when(roleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(added, retained));
        when(accountRepository.findById(99L)).thenReturn(Optional.of(actor));
        when(rolePermissionRepository.findByIdRoleId(10L))
            .thenReturn(List.of(retainedMapping, removedMapping));

        var result = service().replacePermissions(
            10L,
            new ReplaceRolePermissionsRequest(List.of(2L, 1L, 2L)),
            99L
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
        when(roleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> service().replacePermissions(
                10L,
                new ReplaceRolePermissionsRequest(List.of(4L)),
                99L
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
        when(roleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(role));
        when(accountRepository.findById(99L)).thenReturn(Optional.of(actor));
        when(rolePermissionRepository.findByIdRoleId(10L)).thenReturn(List.of(existing));

        var result = service().replacePermissions(
            10L,
            new ReplaceRolePermissionsRequest(List.of()),
            99L
        );

        assertEquals(List.of(), result);
        verify(rolePermissionRepository).deleteAll(List.of(existing));
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void replacePermissionsRejectsUnknownPermissionBeforeChangingMappings() {
        when(roleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(role(false)));
        when(permissionRepository.findAllById(any())).thenReturn(List.of());

        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> service().replacePermissions(
                10L,
                new ReplaceRolePermissionsRequest(List.of(404L)),
                99L
            )
        );

        assertEquals(ErrorCode.PERMISSION_NOT_FOUND, exception.getErrorCode());
        verify(rolePermissionRepository, never()).deleteAll(any());
        verify(rolePermissionRepository, never()).saveAll(any());
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
            .createdAt(Instant.now())
            .build();
    }

    private RoleServiceImpl service() {
        return new RoleServiceImpl(
            roleRepository,
            permissionRepository,
            rolePermissionRepository,
            accountRepository
        );
    }
}
