package com.htttdn.hrm.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;

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
        "PAYROLL_ACCOUNTANT", Set.of(RoleScopeType.COMPANY),
        "PAYROLL_APPROVER", Set.of(RoleScopeType.COMPANY),
        "DIRECTOR", Set.of(RoleScopeType.COMPANY),
        "COMPANY_OWNER", Set.of(RoleScopeType.COMPANY)
    );

    public boolean isRequestable(Role role) {
        return role.getDeletedAt() == null && REQUESTABLE_POLICIES.contains(role.getGrantPolicy());
    }

    public Set<RoleScopeType> allowedScopes(Role role) {
        return SYSTEM_ROLE_SCOPES.getOrDefault(role.getCode(), ALL_SCOPES);
    }

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
