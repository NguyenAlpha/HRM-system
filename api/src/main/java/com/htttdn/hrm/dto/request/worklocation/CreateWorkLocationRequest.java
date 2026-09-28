package com.htttdn.hrm.dto.request.worklocation;

import com.htttdn.hrm.entity.enums.LocationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWorkLocationRequest(
    Long parentLocationId,

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z][A-Z0-9_-]*$")
    String code,

    @NotBlank
    @Size(max = 150)
    String name,

    @NotNull
    LocationType locationType,

    @NotBlank
    String address,

    @Size(max = 20)
    String phone
) {
}
