package com.htttdn.hrm.dto.response.employee;

import com.htttdn.hrm.entity.enums.AccountStatus;

public record EmployeeAccountSummaryResponse(
    Long id,
    String username,
    String email,
    AccountStatus status
) {
}
