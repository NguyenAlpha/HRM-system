package com.htttdn.hrm.service.rbac;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.RolePermissionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Quy tắc role nào được đề nghị cấp và được gán với phạm vi nào.
 *
 * <p>Phạm vi được phép suy ra từ permission của role, áp dụng như nhau cho role hệ thống và role tự tạo: role có
 * permission chỉ dùng được ở phạm vi toàn công ty ({@code Permission.companyWide}, VD {@code payroll.calculate})
 * chỉ được gán {@code COMPANY}; role khác được chọn mọi phạm vi. Không có danh sách phạm vi riêng cho từng role.
 */
@Component
@RequiredArgsConstructor
public class RoleAssignmentPolicy {

    private static final Set<RoleGrantPolicy> REQUESTABLE_POLICIES = Set.of(
        RoleGrantPolicy.HR_ASSIGNABLE,
        RoleGrantPolicy.OWNER_APPROVAL
    );

    private static final List<RoleScopeType> ALL_SCOPES = List.of(RoleScopeType.values());

    private final RolePermissionRepository rolePermissionRepository;

    /** Role chưa xóa và cấp được qua yêu cầu ({@code HR_ASSIGNABLE} hoặc {@code OWNER_APPROVAL}). */
    public boolean isRequestable(Role role) {
        return role.getDeletedAt() == null && REQUESTABLE_POLICIES.contains(role.getGrantPolicy());
    }

    /**
     * Phạm vi được phép của role: chỉ {@code COMPANY} nếu role có permission toàn công ty, ngược lại mọi phạm vi.
     *
     * @return theo thứ tự khai báo của {@link RoleScopeType}, để web hiển thị ổn định
     */
    public List<RoleScopeType> allowedScopes(Role role) {
        return requiresCompanyScope(role) ? List.of(RoleScopeType.COMPANY) : ALL_SCOPES;
    }

    /** Role có permission nào chỉ dùng được ở phạm vi toàn công ty không. */
    public boolean requiresCompanyScope(Role role) {
        return rolePermissionRepository.existsByIdRoleIdAndPermissionCompanyWideTrue(role.getId());
    }

    /**
     * Chặn gán role với phạm vi không hợp lệ.
     *
     * @throws BusinessException phạm vi không thuộc {@link #allowedScopes} (→ 400 {@code VALIDATION_ERROR},
     *         field {@code scopeType})
     */
    public void validateScope(Role role, RoleScopeType scopeType) {
        List<RoleScopeType> allowedScopes = allowedScopes(role);
        if (!allowedScopes.contains(scopeType)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                role.getCode() + " only supports scope " + allowedScopes,
                "scopeType"
            );
        }
    }
}
