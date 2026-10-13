package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;

/**
 * Decides who approves an employee's leave and overtime: the direct manager of the current primary
 * assignment, else the Director, else the Company Owner. A candidate is skipped when it is the
 * employee themself, has left, or has no account that can sign in, so nobody approves their own
 * request. The Company Owner has nobody above. HR checks and aggregates the result but is not part
 * of the chain.
 */
@Service
@Transactional(readOnly = true)
public class ApproverResolver {

    static final String DIRECTOR_ROLE = "DIRECTOR";
    static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";

    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository roleAssignmentRepository;

    public ApproverResolver(
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AccountRepository accountRepository,
        AccountRoleAssignmentRepository roleAssignmentRepository
    ) {
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.accountRepository = accountRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    /** The approver of the employee's requests; empty when nobody stands above them. */
    public Optional<Employee> approverFor(Employee employee) {
        return approverFor(employee, roleHolder(DIRECTOR_ROLE), roleHolder(COMPANY_OWNER_ROLE));
    }

    /** Ids of the given employees whose approver is {@code approver}. */
    public List<Long> decidedBy(Employee approver, List<Employee> employees) {
        Employee director = roleHolder(DIRECTOR_ROLE);
        Employee owner = roleHolder(COMPANY_OWNER_ROLE);
        return employees.stream()
            .filter(employee -> approverFor(employee, director, owner)
                .map(candidate -> candidate.getId().equals(approver.getId()))
                .orElse(false))
            .map(Employee::getId)
            .toList();
    }

    private Optional<Employee> approverFor(Employee employee, Employee director, Employee owner) {
        // The Company Owner is the top of the chart: nobody approves for them.
        if (owner != null && owner.getId().equals(employee.getId())) {
            return Optional.empty();
        }
        Employee manager = employeeAssignmentRepository
            .findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employee.getId())
            .map(EmployeeAssignment::getManagerEmployee)
            .orElse(null);
        return Stream.of(manager, director, owner)
            .filter(Objects::nonNull)
            .filter(candidate -> !candidate.getId().equals(employee.getId()))
            .filter(this::canApprove)
            .findFirst();
    }

    /** Director and Company Owner record their own leave without approval. */
    public boolean isTopManagement(Employee employee) {
        return accountRepository.findByEmployeeId(employee.getId())
            .map(account -> holdsRole(account, DIRECTOR_ROLE) || holdsRole(account, COMPANY_OWNER_ROLE))
            .orElse(false);
    }

    private boolean holdsRole(Account account, String roleCode) {
        return roleAssignmentRepository.findActiveWithRoleByAccountId(account.getId(), LocalDate.now()).stream()
            .anyMatch(assignment -> roleCode.equals(assignment.getRole().getCode()));
    }

    private Employee roleHolder(String roleCode) {
        List<Account> holders = roleAssignmentRepository.findAccountsHoldingRole(roleCode, LocalDate.now());
        return holders.stream().map(Account::getEmployee).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** Still employed and able to sign in to act on the request. */
    private boolean canApprove(Employee candidate) {
        if (candidate.getDeletedAt() != null
            || candidate.getEmploymentStatus() == EmploymentStatus.RESIGNED
            || candidate.getEmploymentStatus() == EmploymentStatus.TERMINATED
            || candidate.getEmploymentStatus() == EmploymentStatus.RETIRED) {
            return false;
        }
        return accountRepository.findByEmployeeId(candidate.getId())
            .map(account -> account.getStatus() == AccountStatus.ACTIVE || account.getStatus() == AccountStatus.LOCKED)
            .orElse(false);
    }
}
