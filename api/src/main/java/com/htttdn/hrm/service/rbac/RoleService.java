package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.role.CreateRoleRequest;
import com.htttdn.hrm.dto.request.role.GrantPermissionRequest;
import com.htttdn.hrm.dto.request.role.ReplaceRolePermissionsRequest;
import com.htttdn.hrm.dto.request.role.UpdateRoleRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.dto.response.role.RoleResponse;
import com.htttdn.hrm.dto.response.role.RoleWithPermissionsResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.security.CanManageRbac;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@CanManageRbac
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final Clock clock;

    public RoleResponse create(CreateRoleRequest request) {
        if (roleRepository.existsByCode(request.code())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Role code is already taken", "code");
        }
        Role role = Role.builder()
            .code(request.code())
            .name(request.name())
            .description(request.description())
            .isSystem(false)
            .grantPolicy(request.grantPolicy())
            .build();

        return toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return toResponse(roleRepository.getRequired(id));
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> list(Pageable pageable) {
        return roleRepository.findByDeletedAtIsNull(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<RoleWithPermissionsResponse> listWithPermissions() {
        List<Role> roles = roleRepository.findByDeletedAtIsNullOrderByIdAsc();
        if (roles.isEmpty()) {
            return List.of();
        }

        Map<Long, List<PermissionResponse>> permissionsByRole = new HashMap<>();
        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        rolePermissionRepository.findAllByRoleIds(roleIds).forEach(rolePermission ->
            permissionsByRole
                .computeIfAbsent(rolePermission.getId().getRoleId(), ignored -> new ArrayList<>())
                .add(toPermissionResponse(rolePermission.getPermission()))
        );

        return roles.stream()
            .map(role -> new RoleWithPermissionsResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getIsSystem(),
                role.getGrantPolicy(),
                List.copyOf(permissionsByRole.getOrDefault(role.getId(), List.of()))
            ))
            .toList();
    }

    public RoleResponse update(Long id, UpdateRoleRequest request) {
        Role role = roleRepository.getRequired(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ConflictException(ErrorCode.CONFLICT, "System roles cannot be modified");
        }
        role.setName(request.name());
        role.setDescription(request.description());
        role.setGrantPolicy(request.grantPolicy());
        return toResponse(role);
    }

    public void softDelete(Long id) {
        Role role = roleRepository.getRequired(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ConflictException(ErrorCode.CONFLICT, "System roles cannot be deleted");
        }
        Instant now = Instant.now(clock);
        role.setDeletedAt(now);
    }

    public void grantPermission(Long roleId, GrantPermissionRequest request) {
        Role role = roleRepository.getRequired(roleId);
        rejectSystemRolePermissionChange(role);
        Permission permission = permissionRepository.getRequired(request.permissionId());
        rejectNonDelegablePermission(permission, "permissionId");

        RolePermissionId id = new RolePermissionId(roleId, request.permissionId());
        if (rolePermissionRepository.existsById(id)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Permission is already granted to this role");
        }

        Account grantedBy = currentAccountProvider.account();

        RolePermission rolePermission = RolePermission.builder()
            .id(id)
            .role(role)
            .permission(permission)
            .createdByAccount(grantedBy)
            .build();

        rolePermissionRepository.save(rolePermission);
    }

    public List<PermissionResponse> replacePermissions(
        Long roleId,
        ReplaceRolePermissionsRequest request
    ) {
        Role role = roleRepository.getRequiredForUpdate(roleId);
        rejectSystemRolePermissionChange(role);

        Set<Long> requestedIds = new LinkedHashSet<>(request.permissionIds());
        List<Permission> requestedPermissions = requestedIds.isEmpty()
            ? List.of()
            : permissionRepository.findAllById(requestedIds);
        Map<Long, Permission> requestedById = requestedPermissions.stream()
            .collect(Collectors.toMap(Permission::getId, Function.identity()));

        for (Long permissionId : requestedIds) {
            Permission permission = requestedById.get(permissionId);
            if (permission == null) {
                throw new ResourceNotFoundException(
                    ErrorCode.PERMISSION_NOT_FOUND,
                    "Permission not found: " + permissionId
                );
            }
            rejectNonDelegablePermission(permission, "permissionIds");
        }

        Account grantedBy = currentAccountProvider.account();
        List<RolePermission> existingMappings = rolePermissionRepository.findByIdRoleId(roleId);
        Map<Long, RolePermission> existingByPermissionId = existingMappings.stream()
            .collect(Collectors.toMap(mapping -> mapping.getPermission().getId(), Function.identity()));

        List<RolePermission> removedMappings = existingMappings.stream()
            .filter(mapping -> !requestedIds.contains(mapping.getPermission().getId()))
            .toList();
        if (!removedMappings.isEmpty()) {
            rolePermissionRepository.deleteAll(removedMappings);
        }
        List<RolePermission> addedMappings = requestedIds.stream()
            .filter(permissionId -> !existingByPermissionId.containsKey(permissionId))
            .map(permissionId -> RolePermission.builder()
                .id(new RolePermissionId(roleId, permissionId))
                .role(role)
                .permission(requestedById.get(permissionId))
                .createdByAccount(grantedBy)
                .build())
            .toList();
        if (!addedMappings.isEmpty()) {
            rolePermissionRepository.saveAll(addedMappings);
        }

        return requestedPermissions.stream()
            .sorted(Comparator.comparing(Permission::getCode))
            .map(this::toPermissionResponse)
            .toList();
    }

    public void revokePermission(Long roleId, Long permissionId) {
        Role role = roleRepository.getRequired(roleId);
        rejectSystemRolePermissionChange(role);
        RolePermissionId id = new RolePermissionId(roleId, permissionId);
        if (!rolePermissionRepository.existsById(id)) {
            throw new ResourceNotFoundException(ErrorCode.PERMISSION_NOT_FOUND, "Role does not have this permission");
        }
        rolePermissionRepository.deleteById(id);
    }

    private void rejectSystemRolePermissionChange(Role role) {
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "System role permissions are defined by the application and cannot be modified"
            );
        }
    }

    private void rejectNonDelegablePermission(Permission permission, String field) {
        if (permission.getAssignmentPolicy() != PermissionAssignmentPolicy.DELEGABLE) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "System-only permissions cannot be assigned to custom roles",
                field
            );
        }
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions(Long roleId) {
        roleRepository.getRequired(roleId);
        return rolePermissionRepository.findByIdRoleId(roleId).stream()
            .map(RolePermission::getPermission)
            .map(this::toPermissionResponse)
            .toList();
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
            permission.getId(),
            permission.getCode(),
            permission.getName(),
            permission.getModule(),
            permission.getDescription(),
            permission.getAssignmentPolicy(),
            permission.getIsActive()
        );
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.getIsSystem(),
            role.getGrantPolicy()
        );
    }
}
