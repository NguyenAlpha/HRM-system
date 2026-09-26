package com.htttdn.hrm.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.security.CanManageRbac;
import com.htttdn.hrm.service.PermissionService;

@Service
@Transactional
@CanManageRbac
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionServiceImpl(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
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

    private Permission findPermissionOrThrow(Long id) {
        return permissionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PERMISSION_NOT_FOUND, "Permission not found: " + id));
    }

    private PermissionResponse toResponse(Permission permission) {
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
}
