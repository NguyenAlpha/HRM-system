package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeDetailResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeePayrollProfileRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.auth.RefreshTokenService;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;

import lombok.RequiredArgsConstructor;

/**
 * Kết thúc làm việc của nhân viên: kết thúc ngay, hẹn ngày nghỉ, hủy hẹn và hoàn tất khi đến hạn. Hồ sơ
 * nhân viên nằm ở {@link EmployeeService}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmploymentLifecycleService {

    private static final String EMPLOYEE_LIFECYCLE_MANAGE = "employee.lifecycle.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final EmployeePayrollProfileRepository employeePayrollProfileRepository;
    private final EmployeeTaxDependentRepository employeeTaxDependentRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final RefreshTokenService refreshTokenService;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final EmployeeMapper employeeMapper;
    private final Clock clock;

    @PreAuthorize("hasAuthority('employee.lifecycle.manage')")
    public EmployeeDetailResponse endEmployment(
        Long employeeId, EmploymentStatus endStatus, LocalDate terminationDate, String terminationReason
    ) {
        if (!isEmploymentEnded(endStatus)) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "endStatus must be RESIGNED, TERMINATED or RETIRED", "endStatus"
            );
        }
        Employee employee = employeeRepository.getRequired(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_LIFECYCLE_MANAGE);
        if (terminationDate.isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "terminationDate must not be before hireDate", "terminationDate"
            );
        }
        if (isEmploymentEnded(employee.getEmploymentStatus())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Employee employment has already ended");
        }

        // The last working day is still worked and paid, so a later one only schedules the end; it
        // replaces any end already scheduled.
        if (terminationDate.isAfter(LocalDate.now(clock))) {
            scheduleEnd(employee, endStatus, terminationDate, terminationReason, currentAccountProvider.account());
        } else {
            endEmploymentAs(employee, endStatus, terminationDate, terminationReason, currentAccountProvider.account());
        }
        return employeeMapper.toDetailResponse(employee);
    }

    @PreAuthorize("hasAuthority('employee.lifecycle.manage')")
    public EmployeeDetailResponse cancelScheduledEnd(Long employeeId) {
        Employee employee = employeeRepository.getRequired(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, EMPLOYEE_LIFECYCLE_MANAGE);
        if (employee.getScheduledEndStatus() == null) {
            throw new ConflictException(ErrorCode.CONFLICT, "No employment end is scheduled");
        }
        employee.setTerminationDate(null);
        employee.setTerminationReason(null);
        employee.setScheduledEndStatus(null);
        employee.setScheduledEndByAccount(null);
        return employeeMapper.toDetailResponse(employee);
    }

    /**
     * Ends a scheduled employment once its last working day has passed, acting as whoever scheduled
     * it. Run by {@link ScheduledEmploymentEndJob}; an end cancelled in the meantime is skipped.
     */
    public void completeScheduledEnd(Long employeeId) {
        Employee employee = employeeRepository.findByIdForUpdate(employeeId).orElse(null);
        if (employee == null || employee.getScheduledEndStatus() == null) {
            return;
        }
        endEmploymentAs(employee, employee.getScheduledEndStatus(), employee.getTerminationDate(),
            employee.getTerminationReason(), employee.getScheduledEndByAccount());
    }

    /**
     * Records the last working day and the status to take after it, leaving the employee working until
     * then. Payroll, attendance and leave already stop at that day.
     */
    private void scheduleEnd(
        Employee employee, EmploymentStatus endStatus, LocalDate terminationDate, String terminationReason, Account actor
    ) {
        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employee.getId())
            .ifPresent(assignment -> requireNotBeforeAssignment(assignment, terminationDate));
        employee.setTerminationDate(terminationDate);
        employee.setTerminationReason(terminationReason);
        employee.setScheduledEndStatus(endStatus);
        employee.setScheduledEndByAccount(actor);
    }

    /**
     * Ends the employment without the caller's permission checks: closes the primary assignment,
     * disables the account and revokes its business roles. Used by {@link #endEmployment} and by an
     * approved resignation once its last working day has passed, where {@code actor} is the approver.
     */
    public void endEmploymentAs(
        Employee employee, EmploymentStatus endStatus, LocalDate terminationDate, String terminationReason, Account actor
    ) {
        Long employeeId = employee.getId();
        employee.setEmploymentStatus(endStatus);
        employee.setTerminationDate(terminationDate);
        employee.setTerminationReason(terminationReason);
        employee.setScheduledEndStatus(null);
        employee.setScheduledEndByAccount(null);

        employeeAssignmentRepository.findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .ifPresent(assignment -> closeAssignmentAtTermination(assignment, terminationDate));
        closePayrollInputsAtTermination(employeeId, terminationDate);
        accountRepository.findByEmployeeId(employeeId).ifPresent(account -> {
            account.setStatus(AccountStatus.DISABLED);
            refreshTokenService.revokeAll(account.getId());
            revokeRolesAtTermination(account.getId(), terminationDate, terminationReason, actor);
        });
    }

    /**
     * Revokes the business roles a leaving employee still holds, so they no longer count as held (a
     * DIRECTOR assignment would otherwise block appointing the next director). Roles that already ended
     * before the termination date stay as history. AUTO and SYSTEM_ONLY roles are left to their own
     * workflows, as the role revoke API does.
     */
    private void revokeRolesAtTermination(Long accountId, LocalDate terminationDate, String terminationReason, Account actor) {
        Instant now = Instant.now();
        for (AccountRoleAssignment assignment : accountRoleAssignmentRepository.findByAccountIdAndRevokedAtIsNull(accountId)) {
            RoleGrantPolicy policy = assignment.getRole().getGrantPolicy();
            if (policy == RoleGrantPolicy.AUTO || policy == RoleGrantPolicy.SYSTEM_ONLY) {
                continue;
            }
            if (assignment.getEffectiveTo() != null && assignment.getEffectiveTo().isBefore(terminationDate)) {
                continue;
            }
            assignment.setRevokedByAccount(actor);
            assignment.setRevokedAt(now);
            assignment.setRevocationReason("Kết thúc làm việc: " + terminationReason.trim());
            if (!assignment.getEffectiveFrom().isAfter(terminationDate)) {
                assignment.setEffectiveTo(terminationDate);
            }
        }
    }

    /**
     * Ends the salary, payroll profile and dependents still running past the last working day there,
     * so none reads as current. Payroll already stops at that day, so no payslip changes. A record
     * starting after the day is left as it is: it never applied.
     */
    private void closePayrollInputsAtTermination(Long employeeId, LocalDate terminationDate) {
        employeeSalaryHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
            .filter(salary -> runsPast(salary.getEffectiveFrom(), salary.getEffectiveTo(), terminationDate))
            .forEach(salary -> salary.setEffectiveTo(terminationDate));
        employeePayrollProfileRepository.findByEmployeeIdForUpdate(employeeId).stream()
            .filter(profile -> runsPast(profile.getEffectiveFrom(), profile.getEffectiveTo(), terminationDate))
            .forEach(profile -> profile.setEffectiveTo(terminationDate));
        employeeTaxDependentRepository.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employeeId).stream()
            .filter(dependent -> runsPast(dependent.getEffectiveFrom(), dependent.getEffectiveTo(), terminationDate))
            .forEach(dependent -> dependent.setEffectiveTo(terminationDate));
    }

    private static boolean runsPast(LocalDate from, LocalDate to, LocalDate terminationDate) {
        return !from.isAfter(terminationDate) && (to == null || to.isAfter(terminationDate));
    }

    private void closeAssignmentAtTermination(EmployeeAssignment assignment, LocalDate terminationDate) {
        requireNotBeforeAssignment(assignment, terminationDate);
        assignment.setEffectiveTo(terminationDate);
    }

    private void requireNotBeforeAssignment(EmployeeAssignment assignment, LocalDate terminationDate) {
        if (terminationDate.isBefore(assignment.getEffectiveFrom())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "terminationDate must not be before the current assignment start date",
                "terminationDate"
            );
        }
    }

    private boolean isEmploymentEnded(EmploymentStatus status) {
        return status == EmploymentStatus.RESIGNED
            || status == EmploymentStatus.TERMINATED
            || status == EmploymentStatus.RETIRED;
    }
}
