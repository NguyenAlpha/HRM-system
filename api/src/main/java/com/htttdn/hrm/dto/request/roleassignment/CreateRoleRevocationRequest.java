package com.htttdn.hrm.dto.request.roleassignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoleRevocationRequest(
    /** The role assignment to take back, from GET /api/accounts/{accountId}/role-assignments. */
    @NotNull
    Long assignmentId,
    @NotBlank
    @Size(max = 100)
    String reason
) {
}
