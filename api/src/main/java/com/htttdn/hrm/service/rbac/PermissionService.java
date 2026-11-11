package com.htttdn.hrm.service.rbac;

import java.util.Arrays;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.specification.PermissionSpecifications;
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

    /**
     * Permission theo module và từ khóa (khớp mã, tên hoặc mô tả, không phân biệt hoa thường), kể cả đã tắt và
     * {@code SYSTEM_ONLY}; tham số {@code null} hoặc rỗng là không lọc.
     */
    @Transactional(readOnly = true)
    public Page<PermissionResponse> list(PermissionModule module, String keyword, Pageable pageable) {
        Specification<Permission> specification = (root, query, builder) -> builder.conjunction();
        for (Specification<Permission> filter : Arrays.asList(
            PermissionSpecifications.inModule(module),
            PermissionSpecifications.matchesKeyword(keyword)
        )) {
            if (filter != null) {
                specification = specification.and(filter);
            }
        }
        return permissionRepository.findAll(specification, pageable).map(PermissionService::toResponse);
    }

    /** Static so {@link RoleService} maps permissions the same way without going through the secured bean. */
    static PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
            permission.getId(),
            permission.getCode(),
            permission.getName(),
            permission.getModule(),
            permission.getDescription(),
            permission.getAssignmentPolicy(),
            permission.isCompanyWide(),
            permission.getIsActive()
        );
    }
}
