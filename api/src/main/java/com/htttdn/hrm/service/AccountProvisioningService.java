package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Internal account bootstrap component shared by administrative workflows.
 * Authorization and transaction boundaries belong to the calling workflow.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AccountProvisioningService {

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

    public AccountProvisioningService(
        AccountRepository accountRepository,
        EmployeeRepository employeeRepository,
        RoleRepository roleRepository,
        AccountRoleAssignmentRepository roleAssignmentRepository,
        AccountActivationService accountActivationService
    ) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.accountActivationService = accountActivationService;
    }

    public AccountProvisioningResponse provisionPendingAccount(
        Long employeeId,
        String requestedUsername,
        Long actorAccountId
    ) {
        Account actor = findAccount(actorAccountId);
        Employee employee = findEligibleEmployee(employeeId);

        if (accountRepository.findByEmployeeId(employee.getId()).isPresent()) {
            throw new ConflictException(
                ErrorCode.EMPLOYEE_ACCOUNT_EXISTS,
                "Employee already has an account",
                "employeeId"
            );
        }

        String username = requestedUsername.trim();
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
        AccountActivationService.IssuedActivationToken issued = accountActivationService.issue(
            account.getId(),
            actorAccountId
        );

        return new AccountProvisioningResponse(
            toResponse(account),
            new AccountInvitationResponse(account.getId(), issued.rawToken(), issued.expiresAt())
        );
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

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found: " + accountId
            ));
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
