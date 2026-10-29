package com.htttdn.hrm.dto.request.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Hands Company Ownership to {@code accountId}; the current owner confirms with their own password. */
public record TransferCompanyOwnerRequest(
    @NotNull
    Long accountId,

    @NotBlank
    @Size(max = 100)
    String password,

    @NotBlank
    @Size(max = 100)
    String reason
) {
}
