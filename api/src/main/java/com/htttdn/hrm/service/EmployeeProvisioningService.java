package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeRepository;

/**
 * Internal building block for workflows that must bootstrap a minimal employee.
 * Authorization belongs to the workflow service that invokes this component.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EmployeeProvisioningService {

    private final EmployeeRepository employeeRepository;

    public EmployeeProvisioningService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee createActiveMinimalEmployee(MinimalEmployeeCommand command) {
        String employeeCode = command.employeeCode().trim();
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(employeeCode)) {
            throw new ConflictException(
                ErrorCode.EMPLOYEE_CODE_TAKEN,
                "Employee code is already taken",
                command.employeeCodeField()
            );
        }

        String workEmail = command.workEmail().trim().toLowerCase(Locale.ROOT);
        if (employeeRepository.existsByWorkEmailIgnoreCase(workEmail)) {
            throw new ConflictException(
                ErrorCode.EMAIL_TAKEN,
                "Work email is already taken",
                command.workEmailField()
            );
        }

        Instant now = Instant.now();
        return employeeRepository.save(Employee.builder()
            .employeeCode(employeeCode)
            .fullName(command.fullName().trim())
            .workEmail(workEmail)
            .phone(normalizeNullable(command.phone()))
            .hireDate(command.hireDate())
            .employmentStatus(EmploymentStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record MinimalEmployeeCommand(
        String employeeCode,
        String fullName,
        String workEmail,
        String phone,
        LocalDate hireDate,
        String employeeCodeField,
        String workEmailField
    ) {
    }
}
