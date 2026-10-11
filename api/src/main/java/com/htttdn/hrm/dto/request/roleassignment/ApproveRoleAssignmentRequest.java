package com.htttdn.hrm.dto.request.roleassignment;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

public record ApproveRoleAssignmentRequest(
    @Size(max = 500)
    @Nullable
    String note
) {
}
