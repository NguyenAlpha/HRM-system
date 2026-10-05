package com.htttdn.hrm.config.seed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionSeederTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private ApplicationArguments applicationArguments;

    @Test
    void hrManagerReceivesBothHrAndPayrollReportPermissions() {
        AtomicLong roleIds = new AtomicLong();
        AtomicLong permissionIds = new AtomicLong();
        Map<String, Role> roles = new HashMap<>();
        Map<String, Permission> permissions = new HashMap<>();

        when(roleRepository.findByCodeAndDeletedAtIsNull(any())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return Optional.of(roles.computeIfAbsent(code, key -> Role.builder()
                .id(roleIds.incrementAndGet())
                .code(key)
                .isSystem(true)
                .build()));
        });
        when(permissionRepository.findByCode(any())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return Optional.of(permissions.computeIfAbsent(code, key -> Permission.builder()
                .id(permissionIds.incrementAndGet())
                .code(key)
                .assignmentPolicy(PermissionAssignmentPolicy.DELEGABLE)
                .build()));
        });
        when(rolePermissionRepository.findAll()).thenReturn(List.of());
        when(rolePermissionRepository.findByIdRoleId(any())).thenReturn(List.of());
        when(rolePermissionRepository.existsById(any())).thenReturn(false);

        new RolePermissionSeeder(
            roleRepository,
            permissionRepository,
            rolePermissionRepository,
            true
        ).run(applicationArguments);

        ArgumentCaptor<RolePermission> captor = ArgumentCaptor.forClass(RolePermission.class);
        verify(rolePermissionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Set<String> hrManagerPermissions = captor.getAllValues().stream()
            .filter(mapping -> "HR_MANAGER".equals(mapping.getRole().getCode()))
            .map(mapping -> mapping.getPermission().getCode())
            .collect(Collectors.toSet());

        assertEquals(
            Set.of("report.hr.read", "report.payroll.read"),
            hrManagerPermissions.stream()
                .filter(code -> code.startsWith("report."))
                .collect(Collectors.toSet())
        );
    }
}
