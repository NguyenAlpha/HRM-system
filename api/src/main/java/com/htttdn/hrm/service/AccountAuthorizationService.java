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
import com.htttdn.hrm.entity.enums.RoleScopeType;
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
        AuthorizationContext context = loadContext(accountId, LocalDate.now());
        if (context.assignments().isEmpty()) {
            return new AuthorizationSnapshot(List.of(), List.of());
        }

        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        for (AccountRoleAssignment assignment : context.assignments()) {
            roles.add(assignment.getRole().getCode());
            permissions.addAll(effectivePermissions(assignment, context));
        }

        return new AuthorizationSnapshot(sorted(roles), sorted(permissions));
    }

    @Transactional(readOnly = true)
    public List<AuthorizationScope> getScopes(Long accountId, String permissionCode) {
        AuthorizationContext context = loadContext(accountId, LocalDate.now());
        return context.assignments().stream()
            .filter(assignment -> effectivePermissions(assignment, context).contains(permissionCode))
            .map(assignment -> new AuthorizationScope(
                assignment.getScopeType(),
                assignment.getOrganizationUnit() == null ? null : assignment.getOrganizationUnit().getId(),
                assignment.getWorkLocation() == null ? null : assignment.getWorkLocation().getId()
            ))
            .distinct()
            .toList();
    }

    private AuthorizationContext loadContext(Long accountId, LocalDate date) {
        List<AccountRoleAssignment> assignments =
            accountRoleAssignmentRepository.findActiveWithRoleByAccountId(accountId, date);
        if (assignments.isEmpty()) {
            return new AuthorizationContext(List.of(), Map.of(), Map.of());
        }

        List<Long> roleIds = assignments.stream()
            .map(assignment -> assignment.getRole().getId())
            .distinct()
            .toList();
        Map<Long, Set<String>> permissionsByRole = groupPermissionsByRole(
            rolePermissionRepository.findActiveByRoleIds(roleIds)
        );

        List<Long> assignmentIds = assignments.stream()
            .map(AccountRoleAssignment::getId)
            .toList();
        Map<Long, List<AccountPermissionOverride>> overridesByAssignment = groupOverridesByAssignment(
            accountPermissionOverrideRepository.findActiveByAssignmentIds(assignmentIds, date)
        );
        return new AuthorizationContext(assignments, permissionsByRole, overridesByAssignment);
    }

    private Set<String> effectivePermissions(
        AccountRoleAssignment assignment,
        AuthorizationContext context
    ) {
        Set<String> permissions = new LinkedHashSet<>(
            context.permissionsByRole().getOrDefault(assignment.getRole().getId(), Set.of())
        );
        for (AccountPermissionOverride permissionOverride :
            context.overridesByAssignment().getOrDefault(assignment.getId(), List.of())) {
            String permissionCode = permissionOverride.getPermission().getCode();
            if (permissionOverride.getEffect() == PermissionOverrideEffect.GRANT) {
                permissions.add(permissionCode);
            } else {
                permissions.remove(permissionCode);
            }
        }
        return permissions;
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

    public record AuthorizationScope(
        RoleScopeType scopeType,
        Long organizationUnitId,
        Long workLocationId
    ) {
    }

    private record AuthorizationContext(
        List<AccountRoleAssignment> assignments,
        Map<Long, Set<String>> permissionsByRole,
        Map<Long, List<AccountPermissionOverride>> overridesByAssignment
    ) {
    }
}
