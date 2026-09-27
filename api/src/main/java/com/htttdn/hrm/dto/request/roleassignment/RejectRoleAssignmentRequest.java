package com.htttdn.hrm.dto.request.roleassignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRoleAssignmentRequest(
    @NotBlank
    @Size(max = 500)
    String note
) {
}
