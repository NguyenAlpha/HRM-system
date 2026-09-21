package com.htttdn.hrm.dto.response.role;

public record RoleResponse(
    Long id,
    String code,
    String name,
    String description,
    Boolean isSystem,
    Boolean isActive
) {
}
