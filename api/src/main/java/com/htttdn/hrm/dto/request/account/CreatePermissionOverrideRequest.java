package com.htttdn.hrm.dto.request.account;

import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePermissionOverrideRequest(
    @NotNull
    @Positive
    Long permissionId,

    @NotNull
    PermissionOverrideEffect effect,

    @NotNull
    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    @NotBlank
    @Size(max = 500)
    String reason
) {
}
