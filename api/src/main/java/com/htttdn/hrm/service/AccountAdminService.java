package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

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
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class AccountAdminService {

    private static final String DEFAULT_EMPLOYEE_ROLE = "EMPLOYEE";
    private static final Set<EmploymentStatus> ACCOUNT_ELIGIBLE_STATUSES = Set.of(
        EmploymentStatus.PROBATION,
        EmploymentStatus.ACTIVE
    );

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;
    private final AccountActivationService accountActivationService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;

    public AccountAdminService(
        AccountRepository accountRepository,
        EmployeeRepository employeeRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository roleAssignmentRepository,
        AccountActivationService accountActivationService,
        RefreshTokenService refreshTokenService,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.accountActivationService = accountActivationService;
        this.refreshTokenService = refreshTokenService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PreAuthorize("hasAuthority('account.manage')")
    public AccountProvisioningResponse create(CreateAccountRequest request) {
        Long actorAccountId = currentAccountProvider.accountId();
        Account actor = findAccount(actorAccountId);
        Employee employee = findEligibleEmployee(request.employeeId());

        if (accountRepository.findByEmployeeId(employee.getId()).isPresent()) {
            throw new ConflictException(
                ErrorCode.EMPLOYEE_ACCOUNT_EXISTS,
                "Employee already has an account",
                "employeeId"
            );
        }

        String username = request.username().trim();
        if (accountRepository.existsByUsername(username)) {
            throw new ConflictException(ErrorCode.USERNAME_TAKEN, "Username is already taken", "username");
        }

        String email = requireWorkEmail(employee);
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already taken", "employeeId");
        }

        Instant now = Instant.now();
        Account account = accountRepository.save(Account.builder()
            .employee(employee)
            .username(username)
            .email(email)
            .passwordHash(null)
            .status(AccountStatus.PENDING)
            .failedLoginCount(0)
            .createdAt(now)
            .updatedAt(now)
            .build());

        assignDefaultEmployeeRole(account, actor, now);
        AccountInvitationResponse invitation = issueInvitation(account.getId(), actorAccountId);
        return new AccountProvisioningResponse(toResponse(account), invitation);
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

    private Employee findEligibleEmployee(Long employeeId) {
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND,
                "Employee not found: " + employeeId
            ));
        if (!ACCOUNT_ELIGIBLE_STATUSES.contains(employee.getEmploymentStatus())) {
            throw new ConflictException(
                ErrorCode.ACCOUNT_PROVISIONING_NOT_ALLOWED,
                "Only active or probationary employees can receive an account",
                "employeeId"
            );
        }
        return employee;
    }

    private String requireWorkEmail(Employee employee) {
        if (employee.getWorkEmail() == null || employee.getWorkEmail().isBlank()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Employee must have a work email before account creation",
                "employeeId"
            );
        }
        return employee.getWorkEmail().trim().toLowerCase(Locale.ROOT);
    }

    private void assignDefaultEmployeeRole(Account account, Account actor, Instant now) {
        Role role = roleRepository.findByCodeAndDeletedAtIsNull(DEFAULT_EMPLOYEE_ROLE)
            .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
            .orElseThrow(() -> new IllegalStateException("Active EMPLOYEE seed role not found"));

        roleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(account)
            .role(role)
            .scopeType(RoleScopeType.SELF)
            .effectiveFrom(LocalDate.now())
            .grantedByAccount(actor)
            .reason("Default employee access assigned during account provisioning")
            .createdAt(now)
            .build());
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
