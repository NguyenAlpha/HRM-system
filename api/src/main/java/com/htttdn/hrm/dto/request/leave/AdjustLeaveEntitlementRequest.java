package com.htttdn.hrm.dto.request.leave;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdjustLeaveEntitlementRequest(
    @NotNull
    Integer adjustmentMinutes,
    @NotBlank
    String reason
) {
}
