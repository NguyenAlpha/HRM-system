package com.htttdn.hrm.service.impl;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.UpdateEmployeeSensitiveRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeSensitiveResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.service.EmployeeAccessScopeService;
import com.htttdn.hrm.service.EmployeeSensitiveDataService;

@Service
@Transactional
public class EmployeeSensitiveDataServiceImpl implements EmployeeSensitiveDataService {

    private static final String SENSITIVE_READ = "employee.sensitive.read";
    private static final String SENSITIVE_MANAGE = "employee.sensitive.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public EmployeeSensitiveDataServiceImpl(
        EmployeeRepository employeeRepository,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.sensitive.read')")
    public EmployeeSensitiveResponse get(Long employeeId) {
        Employee employee = findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, SENSITIVE_READ);
        return toResponse(employee);
    }

    @Override
    @PreAuthorize("hasAuthority('employee.sensitive.manage')")
    public EmployeeSensitiveResponse update(Long employeeId, UpdateEmployeeSensitiveRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, SENSITIVE_MANAGE);
        if (request.nationalId() != null
            && employeeRepository.existsByNationalIdAndIdNot(request.nationalId(), employeeId)) {
            throw new ConflictException(ErrorCode.CONFLICT, "National ID is already taken", "nationalId");
        }

        employee.setNationalId(request.nationalId());
        employee.setPersonalEmail(request.personalEmail());
        employee.setAddress(request.address());
        employee.setTaxCode(request.taxCode());
        employee.setBankName(request.bankName());
        employee.setBankAccountNumber(request.bankAccountNumber());
        employee.setBankAccountHolder(request.bankAccountHolder());
        employee.setUpdatedAt(Instant.now());
        return toResponse(employee);
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
            .filter(employee -> employee.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + id
            ));
    }

    private EmployeeSensitiveResponse toResponse(Employee employee) {
        return new EmployeeSensitiveResponse(
            employee.getId(),
            employee.getNationalId(),
            employee.getPersonalEmail(),
            employee.getAddress(),
            employee.getTaxCode(),
            employee.getBankName(),
            employee.getBankAccountNumber(),
            employee.getBankAccountHolder()
        );
    }
}
