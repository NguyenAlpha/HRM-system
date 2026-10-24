package com.htttdn.hrm.dto.request.ratelimit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** A policy's new quota and on/off state; every field is replaced. */
public record RateLimitPolicyRequest(
    @NotNull
    Boolean enabled,

    @NotNull
    @Min(1)
    @Max(100_000)
    Integer maxRequests,

    /** At most one day. */
    @NotNull
    @Min(1)
    @Max(86_400)
    Integer windowSeconds
) {
}
