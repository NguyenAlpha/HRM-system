package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class EmployeeSalaryHistoryService {

    private static final String COMPENSATION_READ = "compensation.read";
    private static final String COMPENSATION_MANAGE = "compensation.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryHistoryRepository salaryHistoryRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public EmployeeSalaryHistoryService(
        EmployeeRepository employeeRepository,
        EmployeeSalaryHistoryRepository salaryHistoryRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.employeeRepository = employeeRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.accountRepository = accountRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public SalaryHistoryView setSalary(Long employeeId, SetSalaryCommand command) {
        employeeAccessScopeService.requireEmployeeAccess(employeeId, COMPENSATION_MANAGE);
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
            .orElseThrow(() -> employeeNotFound(employeeId));
        validateCommand(employee, command);

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
            .approvedByAccount(findCurrentAccount())
            .reason(normalizeNullable(command.reason()))
            .note(normalizeNullable(command.note()))
            .createdAt(Instant.now())
            .build();
        return toView(salaryHistoryRepository.save(salary));
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
        if (command.baseSalary() == null || command.baseSalary().signum() <= 0) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "baseSalary must be greater than zero",
                "baseSalary"
            );
        }
        if (command.effectiveFrom() == null) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom is required",
                "effectiveFrom"
            );
        }
        if (command.effectiveFrom().isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must not be before employee hireDate",
                "effectiveFrom"
            );
        }
    }

    private void requireReadableEmployee(Long employeeId) {
        employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
            .orElseThrow(() -> employeeNotFound(employeeId));
        employeeAccessScopeService.requireEmployeeAccess(employeeId, COMPENSATION_READ);
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private ResourceNotFoundException employeeNotFound(Long employeeId) {
        return new ResourceNotFoundException(
            ErrorCode.EMPLOYEE_NOT_FOUND,
            "Employee not found: " + employeeId
        );
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        BigDecimal baseSalary,
        LocalDate effectiveFrom,
        String reason,
        String note
    ) {
    }

    public record SalaryHistoryView(
        Long id,
        Long employeeId,
        BigDecimal baseSalary,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long approvedByAccountId,
        String reason,
        String note,
        Instant createdAt
    ) {
    }
}
