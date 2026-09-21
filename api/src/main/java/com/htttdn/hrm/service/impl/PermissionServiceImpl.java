package com.htttdn.hrm.service.impl;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.permission.CreatePermissionRequest;
import com.htttdn.hrm.dto.request.permission.UpdatePermissionRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.service.PermissionService;

@Service
@Transactional
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionServiceImpl(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    public PermissionResponse create(CreatePermissionRequest request) {
        if (permissionRepository.existsByCode(request.code())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Permission code is already taken", "code");
        }

        Permission permission = Permission.builder()
            .code(request.code())
            .module(request.module())
            .description(request.description())
            .isActive(true)
            .createdAt(Instant.now())
            .build();

        return toResponse(permissionRepository.save(permission));
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getById(Long id) {
        return toResponse(findPermissionOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PermissionResponse> list(Pageable pageable) {
        return permissionRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PermissionResponse> listByModule(PermissionModule module, Pageable pageable) {
        return permissionRepository.findByModule(module, pageable).map(this::toResponse);
    }

    @Override
    public PermissionResponse update(Long id, UpdatePermissionRequest request) {
        Permission permission = findPermissionOrThrow(id);
        permission.setDescription(request.description());
        permission.setIsActive(request.isActive());
        return toResponse(permission);
    }

    private Permission findPermissionOrThrow(Long id) {
        return permissionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PERMISSION_NOT_FOUND, "Permission not found: " + id));
    }

    private PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
            permission.getId(),
            permission.getCode(),
            permission.getModule(),
            permission.getDescription(),
            permission.getIsActive()
        );
    }
}
