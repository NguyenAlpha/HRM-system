package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.specification.AccountSpecifications;
import com.htttdn.hrm.repository.specification.SearchPatterns;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
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

    public AccountAdminService(
        AccountRepository accountRepository,
        AccountProvisioningService accountProvisioningService,
        AccountActivationService accountActivationService,
        RefreshTokenService refreshTokenService,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService,
        CompanyOwnerAccountGuard companyOwnerAccountGuard,
        AccountAuthorizationService accountAuthorizationService
    ) {
        this.accountRepository = accountRepository;
        this.accountProvisioningService = accountProvisioningService;
        this.accountActivationService = accountActivationService;
        this.refreshTokenService = refreshTokenService;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.companyOwnerAccountGuard = companyOwnerAccountGuard;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @PreAuthorize("hasAuthority('account.provision')")
    public AccountProvisioningResponse create(CreateAccountRequest request) {
        employeeAccessScopeService.requireEmployeeAccess(request.employeeId(), ACCOUNT_PROVISION);
        return accountProvisioningService.provisionPendingAccount(
            request.employeeId(),
            request.username(),
            currentAccountProvider.accountId()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public Page<AccountResponse> list(AccountSearchCriteria criteria, Pageable pageable) {
        Specification<Account> specification = (root, query, builder) -> builder.conjunction();
        for (Specification<Account> filter : Arrays.asList(
            AccountSpecifications.matchesKeyword(criteria.keyword()),
            AccountSpecifications.hasStatus(criteria.status()),
            AccountSpecifications.hasRole(criteria.roleCode())
        )) {
            if (filter != null) {
                specification = specification.and(filter);
            }
        }
        return accountRepository.findAll(specification, SearchPatterns.requireSortable(pageable, SORTABLE_PROPERTIES))
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public AccountResponse getById(Long accountId) {
        return toResponse(findAccount(accountId));
    }

    @PreAuthorize("hasAuthority('account.read')")
    public List<AccountPermissionGrantResponse> getPermissionGrants(Long accountId) {
        findAccount(accountId);
        return accountAuthorizationService.getPermissionGrants(accountId);
    }

    @PreAuthorize("hasAuthority('account.activation.manage')")
    public AccountInvitationResponse resendInvitation(Long accountId) {
        companyOwnerAccountGuard.requireCanManage(accountId);
        return issueInvitation(accountId, currentAccountProvider.accountId());
    }

    @PreAuthorize("hasAuthority('account.activation.manage')")
    public AccountInvitationResponse initiatePasswordReset(Long accountId) {
        Long actorAccountId = currentAccountProvider.accountId();
        ensureNotSelf(accountId, actorAccountId, "Administrators must use the self-service password change");
        companyOwnerAccountGuard.requireCanManage(accountId);

        Account account = findAccountForUpdate(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE && account.getStatus() != AccountStatus.LOCKED) {
            throw invalidTransition(account, "Password reset requires an active or locked account");
        }

        Instant now = Instant.now();
        account.setPasswordHash(null);
        account.setStatus(AccountStatus.PENDING);
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        account.setUpdatedAt(now);
        refreshTokenService.revokeAll(accountId);
        return issueInvitation(accountId, actorAccountId);
    }

    @PreAuthorize("hasAuthority('account.manage')")
    public AccountResponse suspend(Long accountId) {
        Long actorAccountId = currentAccountProvider.accountId();
        ensureNotSelf(accountId, actorAccountId, "Administrators cannot suspend their own account");
        companyOwnerAccountGuard.requireCanManage(accountId);

        Account account = findAccountForUpdate(accountId);
        if (account.getStatus() == AccountStatus.DISABLED) {
            return toResponse(account);
        }
        if (account.getStatus() == AccountStatus.PENDING) {
            throw invalidTransition(account, "A pending account cannot be suspended");
        }

        account.setStatus(AccountStatus.DISABLED);
        account.setLockedUntil(null);
        account.setUpdatedAt(Instant.now());
        refreshTokenService.revokeAll(accountId);
        accountActivationService.revokeAll(accountId);
        return toResponse(account);
    }

    @PreAuthorize("hasAuthority('account.manage')")
    public AccountResponse activate(Long accountId) {
        Account account = findAccountForUpdate(accountId);
        if (account.getStatus() == AccountStatus.ACTIVE) {
            return toResponse(account);
        }
        if (account.getStatus() == AccountStatus.PENDING || account.getPasswordHash() == null) {
            throw invalidTransition(account, "A pending account must be activated by its invitation token");
        }
        if (account.getStatus() != AccountStatus.DISABLED && account.getStatus() != AccountStatus.LOCKED) {
            throw invalidTransition(account, "Account cannot be activated from its current status");
        }

        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        account.setUpdatedAt(Instant.now());
        return toResponse(account);
    }

    private AccountInvitationResponse issueInvitation(Long accountId, Long actorAccountId) {
        AccountActivationService.IssuedActivationToken issued = accountActivationService.issue(
            accountId,
            actorAccountId
        );
        return new AccountInvitationResponse(accountId, issued.rawToken(), issued.expiresAt());
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private Account findAccountForUpdate(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found: " + accountId
            ));
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

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getEmployee() == null ? null : account.getEmployee().getId(),
            account.getEmployee() == null ? null : account.getEmployee().getEmployeeCode(),
            account.getEmployee() == null ? null : account.getEmployee().getFullName(),
            account.getUsername(),
            account.getEmail(),
            account.getStatus(),
            account.getFailedLoginCount(),
            account.getLockedUntil(),
            account.getLastLoginAt(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }
}
