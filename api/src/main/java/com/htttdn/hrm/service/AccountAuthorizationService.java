package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.AccountPermissionOverride;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;

@Service
public class AccountAuthorizationService {

    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AccountPermissionOverrideRepository accountPermissionOverrideRepository;

    public AccountAuthorizationService(
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        RolePermissionRepository rolePermissionRepository,
        AccountPermissionOverrideRepository accountPermissionOverrideRepository
    ) {
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.accountPermissionOverrideRepository = accountPermissionOverrideRepository;
    }

    @Transactional(readOnly = true)
    public AuthorizationSnapshot getSnapshot(Long accountId) {
        LocalDate today = LocalDate.now();
        List<AccountRoleAssignment> assignments =
            accountRoleAssignmentRepository.findActiveWithRoleByAccountId(accountId, today);

        if (assignments.isEmpty()) {
            return new AuthorizationSnapshot(List.of(), List.of());
        }

        List<Long> roleIds = assignments.stream()
            .map(assignment -> assignment.getRole().getId())
            .distinct()
            .toList();
        List<RolePermission> rolePermissions = rolePermissionRepository.findActiveByRoleIds(roleIds);
        Map<Long, Set<String>> permissionsByRole = groupPermissionsByRole(rolePermissions);

        List<Long> assignmentIds = assignments.stream()
            .map(AccountRoleAssignment::getId)
            .toList();
        List<AccountPermissionOverride> overrides =
            accountPermissionOverrideRepository.findActiveByAssignmentIds(assignmentIds, today);
        Map<Long, List<AccountPermissionOverride>> overridesByAssignment = groupOverridesByAssignment(overrides);

        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        for (AccountRoleAssignment assignment : assignments) {
            roles.add(assignment.getRole().getCode());

            Set<String> assignmentPermissions = new LinkedHashSet<>(
                permissionsByRole.getOrDefault(assignment.getRole().getId(), Set.of())
            );
            for (AccountPermissionOverride permissionOverride :
                overridesByAssignment.getOrDefault(assignment.getId(), List.of())) {
                String permissionCode = permissionOverride.getPermission().getCode();
                if (permissionOverride.getEffect() == PermissionOverrideEffect.GRANT) {
                    assignmentPermissions.add(permissionCode);
                } else {
                    assignmentPermissions.remove(permissionCode);
                }
            }
            permissions.addAll(assignmentPermissions);
        }

        return new AuthorizationSnapshot(sorted(roles), sorted(permissions));
    }

    private Map<Long, Set<String>> groupPermissionsByRole(List<RolePermission> rolePermissions) {
        Map<Long, Set<String>> result = new HashMap<>();
        for (RolePermission rolePermission : rolePermissions) {
            result.computeIfAbsent(rolePermission.getRole().getId(), ignored -> new LinkedHashSet<>())
                .add(rolePermission.getPermission().getCode());
        }
        return result;
    }

    private Map<Long, List<AccountPermissionOverride>> groupOverridesByAssignment(
        List<AccountPermissionOverride> overrides
    ) {
        Map<Long, List<AccountPermissionOverride>> result = new HashMap<>();
        overrides.stream()
            .sorted(Comparator.comparing(AccountPermissionOverride::getCreatedAt)
                .thenComparing(AccountPermissionOverride::getId))
            .forEach(permissionOverride -> result
                .computeIfAbsent(
                    permissionOverride.getAccountRoleAssignment().getId(),
                    ignored -> new ArrayList<>()
                )
                .add(permissionOverride));
        return result;
    }

    private List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    public record AuthorizationSnapshot(List<String> roles, List<String> permissions) {
    }
}
