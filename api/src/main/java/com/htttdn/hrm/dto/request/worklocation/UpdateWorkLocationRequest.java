package com.htttdn.hrm.dto.request.worklocation;

import com.htttdn.hrm.dto.request.ValidationPatterns;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWorkLocationRequest(
    @Nullable
    Long parentLocationId,

    @NotBlank
    @Size(max = 30)
    String name,

    @NotBlank
    String address,

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)

    @Size(max = 12)
    @Nullable
    String phone,

    @NotNull
    Boolean isActive
) {
}
