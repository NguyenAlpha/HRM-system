package com.htttdn.hrm.dto.request.role;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GrantPermissionRequest(
    @NotNull
    @Positive
    Long permissionId
) {
}
