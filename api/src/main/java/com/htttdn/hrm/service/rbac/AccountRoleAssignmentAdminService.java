package com.htttdn.hrm.service.rbac;

import java.util.List;

import com.htttdn.hrm.service.account.CompanyOwnerAccountGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.account.RevokeAccountRoleRequest;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountRoleAssignmentAdminService {

    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final AccountRoleAssignmentMapper roleAssignmentMapper;
    private final CurrentAccountProvider currentAccountProvider;
    private final CompanyOwnerAccountGuard companyOwnerAccountGuard;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public List<AccountRoleAssignmentResponse> list(Long accountId) {
        accountRepository.getRequired(accountId);
        return roleAssignmentRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
            .map(roleAssignmentMapper::toResponse)
            .toList();
    }

    @PreAuthorize("hasAuthority('account.role.assign')")
    public AccountRoleAssignmentResponse assign(Long accountId, AssignAccountRoleRequest request) {
        companyOwnerAccountGuard.requireCanManage(accountId);
        AccountRoleAssignment assignment = roleAssignmentCommandService.assignDirect(
            accountId,
            request,
            currentAccountProvider.account()
        );
        return roleAssignmentMapper.toResponse(assignment);
    }

    @PreAuthorize("hasAuthority('account.role.assign')")
    public AccountRoleAssignmentResponse revoke(
        Long accountId,
        Long assignmentId,
        RevokeAccountRoleRequest request
    ) {
        companyOwnerAccountGuard.requireCanManage(accountId);
        AccountRoleAssignment assignment = roleAssignmentRepository.getRequiredForUpdate(assignmentId, accountId);
        roleAssignmentCommandService.revoke(
            assignment,
            currentAccountProvider.account(),
            request.reason()
        );
        return roleAssignmentMapper.toResponse(assignment);
    }
}
