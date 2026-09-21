package com.htttdn.hrm.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.account.AssignRoleRequest;
import com.htttdn.hrm.dto.request.account.ChangePasswordRequest;
import com.htttdn.hrm.dto.request.account.CreateAccountRequest;
import com.htttdn.hrm.dto.request.account.UpdateAccountStatusRequest;
import com.htttdn.hrm.dto.response.account.AccountResponse;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;

public interface AccountService {

    AccountResponse create(CreateAccountRequest request);

    AccountResponse getById(Long id);

    Page<AccountResponse> list(Pageable pageable);

    AccountResponse updateStatus(Long id, UpdateAccountStatusRequest request);

    void changePassword(Long id, ChangePasswordRequest request);

    AccountRoleAssignmentResponse assignRole(Long accountId, AssignRoleRequest request);

    void revokeRole(Long assignmentId);

    List<AccountRoleAssignmentResponse> listRoleAssignments(Long accountId);
}
