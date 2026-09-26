package com.htttdn.hrm.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.CreateAccountRequest;
import com.htttdn.hrm.dto.response.account.AccountInvitationResponse;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class AccountAdminService {

    private final AccountRepository accountRepository;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountActivationService accountActivationService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;

    public AccountAdminService(
        AccountRepository accountRepository,
        AccountProvisioningService accountProvisioningService,
        AccountActivationService accountActivationService,
        RefreshTokenService refreshTokenService,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.accountRepository = accountRepository;
        this.accountProvisioningService = accountProvisioningService;
        this.accountActivationService = accountActivationService;
        this.refreshTokenService = refreshTokenService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PreAuthorize("hasAuthority('account.manage')")
    public AccountProvisioningResponse create(CreateAccountRequest request) {
        return accountProvisioningService.provisionPendingAccount(
            request.employeeId(),
            request.username(),
            currentAccountProvider.accountId()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public Page<AccountResponse> list(Pageable pageable) {
        return accountRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account.read')")
    public AccountResponse getById(Long accountId) {
        return toResponse(findAccount(accountId));
    }

    @PreAuthorize("hasAuthority('account.activation.manage')")
    public AccountInvitationResponse resendInvitation(Long accountId) {
        return issueInvitation(accountId, currentAccountProvider.accountId());
    }

    @PreAuthorize("hasAuthority('account.activation.manage')")
    public AccountInvitationResponse initiatePasswordReset(Long accountId) {
        Long actorAccountId = currentAccountProvider.accountId();
        ensureNotSelf(accountId, actorAccountId, "Administrators must use the self-service password change");

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
