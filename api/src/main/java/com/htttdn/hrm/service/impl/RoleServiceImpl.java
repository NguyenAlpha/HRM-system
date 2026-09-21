package com.htttdn.hrm.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.role.CreateRoleRequest;
import com.htttdn.hrm.dto.request.role.GrantPermissionRequest;
import com.htttdn.hrm.dto.request.role.UpdateRoleRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.dto.response.role.RoleResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.service.RoleService;

@Service
@Transactional
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AccountRepository accountRepository;

    public RoleServiceImpl(
        RoleRepository roleRepository,
        PermissionRepository permissionRepository,
        RolePermissionRepository rolePermissionRepository,
        AccountRepository accountRepository
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public RoleResponse create(CreateRoleRequest request) {
        if (roleRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Role code is already taken", "code");
        }

        Instant now = Instant.now();
        Role role = Role.builder()
            .code(request.code())
            .name(request.name())
            .description(request.description())
            .isSystem(false)
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return toResponse(roleRepository.save(role));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return toResponse(findRoleOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoleResponse> list(Pageable pageable) {
        return roleRepository.findByDeletedAtIsNull(pageable).map(this::toResponse);
    }

    @Override
    public RoleResponse update(Long id, UpdateRoleRequest request) {
        Role role = findRoleOrThrow(id);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setIsActive(request.isActive());
        role.setUpdatedAt(Instant.now());
        return toResponse(role);
    }

    @Override
    public void softDelete(Long id) {
        Role role = findRoleOrThrow(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ConflictException(ErrorCode.CONFLICT, "System roles cannot be deleted");
        }
        role.setDeletedAt(Instant.now());
        role.setIsActive(false);
    }

    @Override
    public void grantPermission(Long roleId, GrantPermissionRequest request) {
        Role role = findRoleOrThrow(roleId);
        Permission permission = permissionRepository.findById(request.permissionId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PERMISSION_NOT_FOUND, "Permission not found: " + request.permissionId()));

        RolePermissionId id = new RolePermissionId(roleId, request.permissionId());
        if (rolePermissionRepository.existsById(id)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Permission is already granted to this role");
        }

        Account grantedBy = null;
        if (request.grantedByAccountId() != null) {
            grantedBy = accountRepository.findById(request.grantedByAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.grantedByAccountId()));
        }

        RolePermission rolePermission = RolePermission.builder()
            .id(id)
            .role(role)
            .permission(permission)
            .createdByAccount(grantedBy)
            .createdAt(Instant.now())
            .build();

        rolePermissionRepository.save(rolePermission);
    }

    @Override
    public void revokePermission(Long roleId, Long permissionId) {
        RolePermissionId id = new RolePermissionId(roleId, permissionId);
        if (!rolePermissionRepository.existsById(id)) {
            throw new ResourceNotFoundException(ErrorCode.PERMISSION_NOT_FOUND, "Role does not have this permission");
        }
        rolePermissionRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions(Long roleId) {
        findRoleOrThrow(roleId);
        return rolePermissionRepository.findByIdRoleId(roleId).stream()
            .map(RolePermission::getPermission)
            .map(permission -> new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getModule(),
                permission.getDescription(),
                permission.getIsActive()
            ))
            .toList();
    }

    private Role findRoleOrThrow(Long id) {
        return roleRepository.findById(id)
            .filter(role -> role.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, "Role not found: " + id));
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.getIsSystem(),
            role.getIsActive()
        );
    }
}
