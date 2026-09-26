package com.htttdn.hrm.service;

import java.util.List;

import com.htttdn.hrm.dto.request.account.AssignRoleRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;

public interface AccountService {

    AccountRoleAssignmentResponse assignRole(Long accountId, AssignRoleRequest request);

    void revokeRole(Long assignmentId);

    List<AccountRoleAssignmentResponse> listRoleAssignments(Long accountId);
}
