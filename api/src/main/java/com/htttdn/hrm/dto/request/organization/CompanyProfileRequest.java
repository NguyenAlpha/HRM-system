package com.htttdn.hrm.dto.request.organization;

import com.htttdn.hrm.dto.request.ValidationPatterns;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The company's own details. Every field is replaced; null clears an optional one. */
public record CompanyProfileRequest(
    @NotBlank
    @Size(max = 20)
    String code,

    @NotBlank
    @Size(max = 40)
    String name,

    @Size(max = 30)
    @Nullable
    String taxCode,

    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)

    @Size(max = 12)
    @Nullable
    String phone,

    @Email
    @Size(max = 30)
    @Nullable
    String email,

    @Nullable
    String address
) {
}
