package com.htttdn.hrm.dto.response.organization;

import java.time.Instant;

import jakarta.annotation.Nullable;

/**
 * What the sign-in page may show before anyone logs in: the company name and whether it has a logo.
 * {@code logoUpdatedAt} doubles as the logo's version, so a client can bust its cache when it changes.
 */
public record BrandingResponse(
    String name,
    @Nullable Instant logoUpdatedAt
) {
}
