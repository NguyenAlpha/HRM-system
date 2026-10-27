package com.htttdn.hrm.service.rbac;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;

/**
 * Quy tắc tĩnh về role nào được đề nghị cấp và được gán với phạm vi nào.
 *
 * <p>Role hệ thống có nghĩa nghiệp vụ cố định nên bị khóa phạm vi (VD {@code PAYROLL_ACCOUNTANT} chỉ
 * {@code COMPANY} vì kỳ lương là của cả công ty; {@code MANAGER} chỉ theo đơn vị/địa điểm). Role tự tạo
 * (không có trong {@code SYSTEM_ROLE_SCOPES}) được chọn mọi phạm vi.
 */
@Component
public class RoleAssignmentPolicy {

    private static final Set<RoleGrantPolicy> REQUESTABLE_POLICIES = Set.of(
        RoleGrantPolicy.HR_ASSIGNABLE,
        RoleGrantPolicy.OWNER_APPROVAL
    );

    private static final Set<RoleScopeType> ALL_SCOPES = Set.of(
        RoleScopeType.SELF,
        RoleScopeType.COMPANY,
        RoleScopeType.ORG_UNIT,
        RoleScopeType.LOCATION
    );

    private static final Map<String, Set<RoleScopeType>> SYSTEM_ROLE_SCOPES = Map.of(
        "HR_MANAGER", Set.of(RoleScopeType.COMPANY),
        "MANAGER", Set.of(RoleScopeType.ORG_UNIT, RoleScopeType.LOCATION),
        "PAYROLL_ACCOUNTANT", Set.of(RoleScopeType.COMPANY),
        "PAYROLL_APPROVER", Set.of(RoleScopeType.COMPANY),
        "DIRECTOR", Set.of(RoleScopeType.COMPANY),
        "COMPANY_OWNER", Set.of(RoleScopeType.COMPANY)
    );

    /** Role chưa xóa và cấp được qua yêu cầu ({@code HR_ASSIGNABLE} hoặc {@code OWNER_APPROVAL}). */
    public boolean isRequestable(Role role) {
        return role.getDeletedAt() == null && REQUESTABLE_POLICIES.contains(role.getGrantPolicy());
    }

    /** Phạm vi được phép của role: theo bảng role hệ thống, role khác được mọi phạm vi. */
    public Set<RoleScopeType> allowedScopes(Role role) {
        return SYSTEM_ROLE_SCOPES.getOrDefault(role.getCode(), ALL_SCOPES);
    }

    /**
     * Chặn gán role với phạm vi không hợp lệ.
     *
     * @throws BusinessException phạm vi không thuộc {@link #allowedScopes} (→ 400 {@code VALIDATION_ERROR},
     *         field {@code scopeType})
     */
    public void validateScope(Role role, RoleScopeType scopeType) {
        Set<RoleScopeType> allowedScopes = allowedScopes(role);
        if (!allowedScopes.contains(scopeType)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                role.getCode() + " only supports scope " + allowedScopes,
                "scopeType"
            );
        }
    }
}
