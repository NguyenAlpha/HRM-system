package com.htttdn.hrm.dto.request.organization;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The company's own details. Every field is replaced; null clears an optional one. */
public record CompanyProfileRequest(
    @NotBlank
    @Size(max = 30)
    String code,

    @NotBlank
    @Size(max = 200)
    String name,

    @Size(max = 30)
    @Nullable
    String taxCode,

    @Size(max = 20)
    @Nullable
    String phone,

    @Email
    @Size(max = 100)
    @Nullable
    String email,

    @Nullable
    String address
) {
}
