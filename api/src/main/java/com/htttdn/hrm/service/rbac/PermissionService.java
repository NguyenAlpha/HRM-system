package com.htttdn.hrm.service.rbac;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.security.CanManageRbac;

import lombok.RequiredArgsConstructor;

/**
 * Danh mục permission (chỉ đọc) cho màn quản lý role.
 *
 * <p>Permission do ứng dụng định nghĩa và nạp sẵn bằng seeder, không tạo/sửa qua API. {@link CanManageRbac}
 * trên class → mọi method cần {@code rbac.manage}.
 */
@Service
@Transactional
@CanManageRbac
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    /** @throws com.htttdn.hrm.exception.ResourceNotFoundException không có permission (→ 404 {@code PERMISSION_NOT_FOUND}) */
    @Transactional(readOnly = true)
    public PermissionResponse getById(Long id) {
        return toResponse(permissionRepository.getRequired(id));
    }

    /** Mọi permission, kể cả đã tắt và {@code SYSTEM_ONLY}. */
    @Transactional(readOnly = true)
    public Page<PermissionResponse> list(Pageable pageable) {
        return permissionRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PermissionResponse> listByModule(PermissionModule module, Pageable pageable) {
        return permissionRepository.findByModule(module, pageable).map(this::toResponse);
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
