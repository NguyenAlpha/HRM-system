package com.htttdn.hrm.dto.request.role;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReplaceRolePermissionsRequest(
    @NotNull
    @Size(max = 500)
    List<@Valid @NotNull @Positive Long> permissionIds
) {
}
