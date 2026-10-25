package com.htttdn.hrm.service.employee;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employee.UpdateEmployeeSensitiveRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.employee.EmployeeSensitiveResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeSensitiveDataService {

    private static final String SENSITIVE_READ = "employee.sensitive.read";
    private static final String SENSITIVE_MANAGE = "employee.sensitive.manage";

    private final EmployeeRepository employeeRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('employee.sensitive.read')")
    public EmployeeSensitiveResponse get(Long employeeId) {
        Employee employee = employeeRepository.getRequired(employeeId);
        employeeAccessScopeService.requireEmployeeAccess(employeeId, SENSITIVE_READ);
        return toResponse(employee);
    }

    @PreAuthorize("hasAuthority('employee.sensitive.manage')")
    public EmployeeSensitiveResponse update(Long employeeId, UpdateEmployeeSensitiveRequest request) {
        Employee employee = employeeRepository.getRequired(employeeId);
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
        return toResponse(employee);
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
