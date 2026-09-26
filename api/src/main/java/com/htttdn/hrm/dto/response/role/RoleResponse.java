package com.htttdn.hrm.dto.response.role;

import com.htttdn.hrm.entity.enums.RoleGrantPolicy;

public record RoleResponse(
    Long id,
    String code,
    String name,
    String description,
    Boolean isSystem,
    RoleGrantPolicy grantPolicy
) {
}
