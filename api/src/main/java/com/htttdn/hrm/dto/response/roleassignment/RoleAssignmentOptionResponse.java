package com.htttdn.hrm.dto.response.roleassignment;

import java.util.List;

import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;

import jakarta.annotation.Nullable;

public record RoleAssignmentOptionResponse(
    Long roleId,
    String roleCode,
    String roleName,
    @Nullable String roleDescription,
    RoleGrantPolicy grantPolicy,
    boolean requiresApproval,
    List<RoleScopeType> allowedScopeTypes
) {
}
