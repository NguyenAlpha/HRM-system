package com.htttdn.hrm.dto.response.organization;

import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;

public record OrganizationDirectorProvisioningResponse(
    Long employeeId,
    String employeeCode,
    String fullName,
    AccountProvisioningResponse accountProvisioning,
    AccountRoleAssignmentResponse directorRoleAssignment
) {
}
