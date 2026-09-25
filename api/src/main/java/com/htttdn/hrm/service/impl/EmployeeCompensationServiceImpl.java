package com.htttdn.hrm.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.compensation.SetCompensationRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.compensation.EmployeeCompensationResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeCompensation;
import com.htttdn.hrm.entity.enums.CompensationType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeCompensationRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.EmployeeAccessScopeService;
import com.htttdn.hrm.service.EmployeeCompensationService;

@Service
@Transactional
public class EmployeeCompensationServiceImpl implements EmployeeCompensationService {

    private static final String COMPENSATION_READ = "compensation.read";
    private static final String COMPENSATION_MANAGE = "compensation.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeCompensationRepository employeeCompensationRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public EmployeeCompensationServiceImpl(
        EmployeeRepository employeeRepository,
        EmployeeCompensationRepository employeeCompensationRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeCompensationRepository = employeeCompensationRepository;
        this.accountRepository = accountRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    @Override
    @PreAuthorize("hasAuthority('compensation.manage')")
    public EmployeeCompensationResponse set(Long employeeId, SetCompensationRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, COMPENSATION_MANAGE);
        if (request.effectiveFrom().isBefore(employee.getHireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must not be before employee hireDate",
                "effectiveFrom"
            );
        }

        Account approvedBy = findCurrentAccount();
        List<EmployeeCompensation> existing =
            employeeCompensationRepository.findByEmployeeIdAndEffectiveToIsNull(employeeId);
        LocalDate closingDate = request.effectiveFrom().minusDays(1);
        for (EmployeeCompensation compensation : existing) {
            boolean sameCode = compensation.getComponentCode().equals(request.componentCode());
            boolean bothBasicSalary = compensation.getComponentType() == CompensationType.BASIC_SALARY
                && request.componentType() == CompensationType.BASIC_SALARY;
            if (sameCode || bothBasicSalary) {
                if (!request.effectiveFrom().isAfter(compensation.getEffectiveFrom())) {
                    throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "effectiveFrom must be after the current compensation start date",
                        "effectiveFrom"
                    );
                }
                compensation.setEffectiveTo(closingDate);
            }
        }

        EmployeeCompensation compensation = EmployeeCompensation.builder()
            .employee(employee)
            .componentType(request.componentType())
            .componentCode(request.componentCode())
            .componentName(request.componentName())
            .monthlyAmount(request.monthlyAmount())
            .effectiveFrom(request.effectiveFrom())
            .approvedByAccount(approvedBy)
            .note(request.note())
            .createdAt(Instant.now())
            .build();

        return toResponse(employeeCompensationRepository.save(compensation));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('compensation.read')")
    public List<EmployeeCompensationResponse> getActive(Long employeeId, LocalDate asOfDate) {
        findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, COMPENSATION_READ);
        return employeeCompensationRepository.findByEmployeeId(employeeId).stream()
            .filter(compensation -> !compensation.getEffectiveFrom().isAfter(asOfDate))
            .filter(compensation -> compensation.getEffectiveTo() == null
                || !compensation.getEffectiveTo().isBefore(asOfDate))
            .map(this::toResponse)
            .toList();
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
            .filter(employee -> employee.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + id
            ));
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + accountId
            ));
    }

    private EmployeeCompensationResponse toResponse(EmployeeCompensation compensation) {
        return new EmployeeCompensationResponse(
            compensation.getId(),
            compensation.getEmployee().getId(),
            compensation.getComponentType(),
            compensation.getComponentCode(),
            compensation.getComponentName(),
            compensation.getMonthlyAmount(),
            compensation.getEffectiveFrom(),
            compensation.getEffectiveTo()
        );
    }
}
