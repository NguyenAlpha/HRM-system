package com.htttdn.hrm.dto.request.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeAccountRoleRequest(
    @NotBlank
    @Size(max = 100)
    String reason
) {
}
