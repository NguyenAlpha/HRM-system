package com.htttdn.hrm.service;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Keeps holders of account-management permissions from taking over a Company Owner or System Admin
 * account: only another Company Owner may suspend a Company Owner, issue it a new activation token, or
 * change its roles and permission overrides, and likewise only another System Admin for a System Admin.
 * So the system administrator cannot seize the owner, nor the owner the administrator. Reactivating a
 * locked or disabled account stays open because it cannot hand the account to someone else.
 */
@Component
@RequiredArgsConstructor
public class CompanyOwnerAccountGuard {

    static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";
    static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";

    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final CurrentAccountProvider currentAccountProvider;

    public void requireCanManage(Long targetAccountId) {
        Long actorAccountId = currentAccountProvider.accountId();
        if (hasRole(targetAccountId, COMPANY_OWNER_ROLE) && !hasRole(actorAccountId, COMPANY_OWNER_ROLE)) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "Only a Company Owner can change the access of a Company Owner account"
            );
        }
        if (hasRole(targetAccountId, SYSTEM_ADMIN_ROLE) && !hasRole(actorAccountId, SYSTEM_ADMIN_ROLE)) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "Only a System Admin can change the access of a System Admin account"
            );
        }
    }

    private boolean hasRole(Long accountId, String roleCode) {
        return roleAssignmentRepository.existsByAccountIdAndRoleCodeAndRevokedAtIsNull(accountId, roleCode);
    }
}
