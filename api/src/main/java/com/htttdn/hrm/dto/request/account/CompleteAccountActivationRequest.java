package com.htttdn.hrm.dto.request.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteAccountActivationRequest(
    @NotBlank
    @Size(min = 8, max = 100)
    String password,

    @NotBlank
    @Size(max = 100)
    String passwordConfirmation
) {
}
