package com.htttdn.hrm.dto.request.account;

import com.htttdn.hrm.entity.enums.AccountStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
    @NotNull
    AccountStatus status
) {
}
