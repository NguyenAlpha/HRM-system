package com.htttdn.hrm.dto.request.employeerequest;

import jakarta.validation.constraints.NotNull;

public record ReviewRequestRequest(
    @NotNull
    Long reviewerAccountId,

    String comment
) {
}
