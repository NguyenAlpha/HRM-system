package com.htttdn.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Internal account bootstrap component shared by administrative workflows.
 * Authorization and transaction boundaries belong to the calling workflow.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
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
    private final Clock clock;

    public AccountProvisioningResponse provisionPendingAccount(
        Long employeeId,
        String requestedUsername,
        Long actorAccountId
    ) {
        Account actor = accountRepository.getRequired(actorAccountId);
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
            .build());

        assignDefaultEmployeeRole(account, actor, now);
        AccountActivationService.IssuedActivationToken issued = accountActivationService.issue(
            account.getId(),
            actorAccountId
        );

        return new AccountProvisioningResponse(
            toResponse(account, List.of(DEFAULT_EMPLOYEE_ROLE)),
            new AccountInvitationResponse(account.getId(), issued.rawToken(), issued.expiresAt())
        );
    }

    private Employee findEligibleEmployee(Long employeeId) {
        Employee employee = employeeRepository.getRequired(employeeId);
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
            .orElseThrow(() -> new IllegalStateException("EMPLOYEE seed role not found"));

        roleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(account)
            .role(role)
            .scopeType(RoleScopeType.SELF)
            .effectiveFrom(LocalDate.now(clock))
            .grantedByAccount(actor)
            .reason("Default employee access assigned during account provisioning")
            .build());
    }

    private AccountResponse toResponse(Account account, List<String> roleCodes) {
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
            account.getLockedUntil(),
            account.getLastLoginAt(),
            account.getCreatedAt(),
            account.getUpdatedAt(),
            account.getDeletedAt(),
            account.getDeletionReason()
        );
    }
}
