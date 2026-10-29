package com.htttdn.hrm.service.account;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.SystemRoles;

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

    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final CurrentAccountProvider currentAccountProvider;

    public void requireCanManage(Long targetAccountId) {
        Long actorAccountId = currentAccountProvider.accountId();
        if (hasRole(targetAccountId, SystemRoles.COMPANY_OWNER) && !hasRole(actorAccountId, SystemRoles.COMPANY_OWNER)) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "Only a Company Owner can change the access of a Company Owner account"
            );
        }
        if (hasRole(targetAccountId, SystemRoles.SYSTEM_ADMIN) && !hasRole(actorAccountId, SystemRoles.SYSTEM_ADMIN)) {
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
