package com.htttdn.hrm.service;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

/**
 * Keeps holders of account-management permissions (for example the system administrator) from taking
 * over a Company Owner account: only another Company Owner may suspend it, issue it a new activation
 * token, or change its roles and permission overrides. Reactivating a locked or disabled owner stays
 * open because it cannot hand the account to someone else.
 */
@Component
public class CompanyOwnerAccountGuard {

    static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";

    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final CurrentAccountProvider currentAccountProvider;

    public CompanyOwnerAccountGuard(
        AccountRoleAssignmentRepository roleAssignmentRepository,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.currentAccountProvider = currentAccountProvider;
    }

    public void requireCanManage(Long targetAccountId) {
        if (!isCompanyOwner(targetAccountId) || isCompanyOwner(currentAccountProvider.accountId())) {
            return;
        }
        throw new ForbiddenException(
            ErrorCode.FORBIDDEN,
            "Only a Company Owner can change the access of a Company Owner account"
        );
    }

    private boolean isCompanyOwner(Long accountId) {
        return roleAssignmentRepository.existsByAccountIdAndRoleCodeAndRevokedAtIsNull(accountId, COMPANY_OWNER_ROLE);
    }
}
