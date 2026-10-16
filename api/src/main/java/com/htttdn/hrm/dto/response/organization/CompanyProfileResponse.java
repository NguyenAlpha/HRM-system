package com.htttdn.hrm.dto.response.organization;

import java.time.Instant;

import jakarta.annotation.Nullable;

public record CompanyProfileResponse(
    String code,
    String name,
    @Nullable String taxCode,
    @Nullable String phone,
    @Nullable String email,
    @Nullable String address,
    Instant updatedAt
) {
}
