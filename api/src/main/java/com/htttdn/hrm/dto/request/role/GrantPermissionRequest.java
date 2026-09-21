package com.htttdn.hrm.dto.request.role;

import jakarta.validation.constraints.NotNull;

public record GrantPermissionRequest(
    @NotNull
    Long permissionId,

    Long grantedByAccountId
) {
}
