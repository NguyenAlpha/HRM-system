package com.htttdn.hrm.service.account;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.htttdn.hrm.service.rbac.AccountAuthorizationService;
import com.htttdn.hrm.service.rbac.AccountRoleAssignmentCommandService;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import com.htttdn.hrm.service.auth.RefreshTokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AccountSearchCriteria;
import com.htttdn.hrm.dto.request.account.CreateAccountRequest;
import com.htttdn.hrm.dto.response.account.AccountInvitationResponse;
import com.htttdn.hrm.dto.response.account.AccountPermissionGrantResponse;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.specification.AccountSpecifications;
import com.htttdn.hrm.repository.specification.SearchPatterns;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountAdminService {

    private static final String ACCOUNT_PROVISION = "account.provision";

    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
        "id", "username", "email", "status", "lastLoginAt", "createdAt"
    );

    private final AccountRepository accountRepository;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountActivationService accountActivationService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final CompanyOwnerAccountGuard companyOwnerAccountGuard;
    private final AccountAuthorizationService accountAuthorizationService;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final Clock clock;

    @PreAuthorize("hasAuthority('account.provision')")
    public AccountProvisioningResponse create(CreateAccountRequest request) {
        employeeAccessScopeService.requireEmployeeAccess(request.employeeId(), ACCOUNT_PROVISION);
        return accountProvisioningService.provisionPendingAccount(
            request.employeeId(),
            request.username(),
            currentAccountProvider.account()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public Page<AccountResponse> list(AccountSearchCriteria criteria, Pageable pageable) {
        Specification<Account> specification = (root, query, builder) -> builder.conjunction();
        for (Specification<Account> filter : Arrays.asList(
            AccountSpecifications.isDeleted(criteria.deleted()),
            AccountSpecifications.matchesKeyword(criteria.keyword()),
            AccountSpecifications.hasStatus(criteria.status()),
            AccountSpecifications.hasRole(criteria.roleCode())
        )) {
            if (filter != null) {
                specification = specification.and(filter);
            }
        }
        Page<Account> accounts =
            accountRepository.findAll(specification, SearchPatterns.requireSortable(pageable, SORTABLE_PROPERTIES));
        Map<Long, List<String>> roleCodes = heldRoleCodes(accounts.map(Account::getId).getContent());
        return accounts.map(account -> toResponse(account, roleCodes.getOrDefault(account.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public AccountResponse getById(Long accountId) {
        return toResponse(accountRepository.getRequired(accountId));
    }

    @PreAuthorize("hasAuthority('account.read')")
    public List<AccountPermissionGrantResponse> getPermissionGrants(Long accountId) {
        accountRepository.getRequired(accountId);
        return accountAuthorizationService.getPermissionGrants(accountId);
    }

    @PreAuthorize("hasAuthority('account.activation.manage')")
    public AccountInvitationResponse resendInvitation(Long accountId) {
        companyOwnerAccountGuard.requireCanManage(accountId);
        return issueInvitation(accountId);
    }

    @PreAuthorize("hasAuthority('account.activation.manage')")
    public AccountInvitationResponse initiatePasswordReset(Long accountId) {
        Long actorAccountId = currentAccountProvider.accountId();
        ensureNotSelf(accountId, actorAccountId, "Administrators must use the self-service password change");
        companyOwnerAccountGuard.requireCanManage(accountId);

        Account account = accountRepository.getRequiredForUpdate(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw invalidTransition(account, "Password reset requires an active account");
        }
        account.setPasswordHash(null);
        account.setStatus(AccountStatus.PENDING);
        account.setFailedLoginCount(0);
        refreshTokenService.revokeAll(accountId);
        return issueInvitation(accountId);
    }

    @PreAuthorize("hasAuthority('account.manage')")
    public AccountResponse suspend(Long accountId) {
        Long actorAccountId = currentAccountProvider.accountId();
        ensureNotSelf(accountId, actorAccountId, "Administrators cannot suspend their own account");
        companyOwnerAccountGuard.requireCanManage(accountId);

        Account account = accountRepository.getRequiredForUpdate(accountId);
        if (account.getStatus() == AccountStatus.DISABLED) {
            return toResponse(account);
        }
        if (account.getStatus() == AccountStatus.PENDING) {
            throw invalidTransition(account, "A pending account cannot be suspended");
        }

        account.setStatus(AccountStatus.DISABLED);
        refreshTokenService.revokeAll(accountId);
        accountActivationService.revokeAll(accountId);
        return toResponse(accountRepository.saveAndFlush(account));
    }

    @PreAuthorize("hasAuthority('account.manage')")
    public AccountResponse activate(Long accountId) {
        Account account = accountRepository.getRequiredForUpdate(accountId);
        if (account.getDeletedAt() != null) {
            throw invalidTransition(account, "A deleted account cannot be reactivated");
        }
        // Ending employment disables the account; reactivating it would let the person who left sign in again.
        if (account.getEmployee() != null && account.getEmployee().getEmploymentStatus().isEnded()) {
            throw invalidTransition(account, "The account of an employee who has left cannot be reactivated");
        }
        if (account.getStatus() == AccountStatus.ACTIVE) {
            return toResponse(account);
        }
        if (account.getStatus() == AccountStatus.PENDING || account.getPasswordHash() == null) {
            throw invalidTransition(account, "A pending account must be activated by its invitation token");
        }

        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedLoginCount(0);
        return toResponse(accountRepository.saveAndFlush(account));
    }

    /**
     * Soft-deletes an account: it is disabled for good, its sessions, activation tokens and business roles
     * are revoked, and it leaves the account list. The row stays, so everything it approved or recorded
     * still names it, and its username and email are not reused. The employee may get a new account.
     */
    @PreAuthorize("hasAuthority('account.manage')")
    public AccountResponse delete(Long accountId, String reason) {
        Long actorAccountId = currentAccountProvider.accountId();
        ensureNotSelf(accountId, actorAccountId, "Administrators cannot delete their own account");
        companyOwnerAccountGuard.requireCanManage(accountId);

        Account account = accountRepository.getRequiredForUpdate(accountId);
        if (account.getDeletedAt() != null) {
            throw new ConflictException(ErrorCode.ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED, "Account is already deleted");
        }
        Instant now = Instant.now(clock);
        Account actor = accountRepository.getReferenceById(actorAccountId);
        String trimmed = reason.trim();
        account.setStatus(AccountStatus.DISABLED);
        account.setDeletedAt(now);
        account.setDeletedByAccount(actor);
        account.setDeletionReason(trimmed);
        refreshTokenService.revokeAll(accountId);
        accountActivationService.revokeAll(accountId);
        // As at termination, AUTO and SYSTEM_ONLY roles are left to their own workflows.
        roleAssignmentCommandService.revokeHeldRoles(accountId, LocalDate.now(clock), actor, "Xóa tài khoản: " + trimmed);
        return toResponse(accountRepository.saveAndFlush(account));
    }

    private AccountInvitationResponse issueInvitation(Long accountId) {
        AccountActivationService.IssuedActivationToken issued = accountActivationService.issue(
            accountId,
            currentAccountProvider.account()
        );
        return new AccountInvitationResponse(accountId, issued.rawToken(), issued.expiresAt());
    }

    private void ensureNotSelf(Long targetAccountId, Long actorAccountId, String message) {
        if (targetAccountId.equals(actorAccountId)) {
            throw new ConflictException(ErrorCode.ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED, message);
        }
    }

    private ConflictException invalidTransition(Account account, String message) {
        return new ConflictException(
            ErrorCode.ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED,
            message + ": " + account.getStatus()
        );
    }

    private Map<Long, List<String>> heldRoleCodes(List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (Object[] row : roleAssignmentRepository.findHeldRoleCodes(accountIds)) {
            List<String> codes = result.computeIfAbsent((Long) row[0], ignored -> new ArrayList<>());
            if (!codes.contains((String) row[1])) {
                codes.add((String) row[1]);
            }
        }
        result.values().forEach(Collections::sort);
        return result;
    }

    private AccountResponse toResponse(Account account) {
        return toResponse(account, heldRoleCodes(List.of(account.getId())).getOrDefault(account.getId(), List.of()));
    }

    static AccountResponse toResponse(Account account, List<String> roleCodes) {
        return new AccountResponse(
            account.getId(),
            account.getEmployee() == null ? null : account.getEmployee().getId(),
            account.getEmployee() == null ? null : account.getEmployee().getEmployeeCode(),
            account.getEmployee() == null ? null : account.getEmployee().getFullName(),
            account.getUsername(),
            account.getEmail(),
            account.getStatus(),
            roleCodes,
            account.getFailedLoginCount(),
            account.getLastLoginAt(),
            account.getCreatedAt(),
            account.getUpdatedAt(),
            account.getDeletedAt(),
            account.getDeletionReason()
        );
    }
}
