package com.htttdn.hrm.service.employee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.service.payroll.PayrollPeriodInvalidator;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeSalaryHistoryService {

    private static final String COMPENSATION_READ = "compensation.read";
    private static final String COMPENSATION_MANAGE = "compensation.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryHistoryRepository salaryHistoryRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final PayrollPeriodInvalidator payrollPeriodInvalidator;

    @PreAuthorize("hasAuthority('compensation.manage')")
    public SalaryHistoryView setSalary(Long employeeId, SetSalaryCommand command) {
        employeeAccessScopeService.requireEmployeeAccess(employeeId, COMPENSATION_MANAGE);
        Employee employee = employeeRepository.getRequiredForUpdate(employeeId);
        validateCommand(employee, command);

        // Before closing the current salary: a released period makes the whole change fail.
        payrollPeriodInvalidator.invalidateFrom(command.effectiveFrom());

        List<EmployeeSalaryHistory> openSalaries = salaryHistoryRepository
            .findOpenByEmployeeIdForUpdate(employeeId);
        if (openSalaries.size() > 1) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Employee has multiple open salary records"
            );
        }
        openSalaries.stream().findFirst().ifPresent(current -> {
            if (!command.effectiveFrom().isAfter(current.getEffectiveFrom())) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "effectiveFrom must be after the current salary start date",
                    "effectiveFrom"
                );
            }
            current.setEffectiveTo(command.effectiveFrom().minusDays(1));
        });
        salaryHistoryRepository.flush();

        EmployeeSalaryHistory salary = EmployeeSalaryHistory.builder()
            .employee(employee)
            .baseSalary(command.baseSalary())
            .effectiveFrom(command.effectiveFrom())
            .approvedByAccount(currentAccountProvider.account())
            .reason(Strings.trimToNull(command.reason()))
            .note(Strings.trimToNull(command.note()))
            .build();
        SalaryHistoryView result = toView(salaryHistoryRepository.save(salary));
        return result;
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public SalaryHistoryView getEffective(Long employeeId, LocalDate date) {
        requireReadableEmployee(employeeId);
        return salaryHistoryRepository.findEffective(employeeId, date)
            .map(this::toView)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "No salary is effective for employee " + employeeId + " at " + date
            ));
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<SalaryHistoryView> listHistory(Long employeeId) {
        requireReadableEmployee(employeeId);
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
            .map(this::toView)
            .toList();
    }

    private void validateCommand(Employee employee, SetSalaryCommand command) {
        if (command.effectiveFrom().isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must not be before employee hireDate",
                "effectiveFrom"
            );
        }
        EmploymentEndGuard.requireNotAfterEnd(employee, command.effectiveFrom(), "effectiveFrom");
    }

    private void requireReadableEmployee(Long employeeId) {
        employeeRepository.getRequired(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, COMPENSATION_READ);
    }

    private SalaryHistoryView toView(EmployeeSalaryHistory salary) {
        return new SalaryHistoryView(
            salary.getId(),
            salary.getEmployee().getId(),
            salary.getBaseSalary(),
            salary.getEffectiveFrom(),
            salary.getEffectiveTo(),
            salary.getApprovedByAccount().getId(),
            salary.getReason(),
            salary.getNote(),
            salary.getCreatedAt()
        );
    }

    public record SetSalaryCommand(
        @NotNull @Positive BigDecimal baseSalary,
        @NotNull LocalDate effectiveFrom,
        @Nullable String reason,
        @Nullable String note
    ) {
    }

    public record SalaryHistoryView(
        Long id,
        Long employeeId,
        BigDecimal baseSalary,
        LocalDate effectiveFrom,
        @Nullable LocalDate effectiveTo,
        Long approvedByAccountId,
        @Nullable String reason,
        @Nullable String note,
        Instant createdAt
    ) {
    }
}
