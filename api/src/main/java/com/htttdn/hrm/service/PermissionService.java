package com.htttdn.hrm.service;

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

@Service
@Transactional
@CanManageRbac
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public PermissionResponse getById(Long id) {
        return toResponse(findPermissionOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<PermissionResponse> list(Pageable pageable) {
        return permissionRepository.findAll(pageable).map(this::toResponse);
    }

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
