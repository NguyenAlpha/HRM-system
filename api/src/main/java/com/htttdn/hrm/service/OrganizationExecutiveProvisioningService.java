package com.htttdn.hrm.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.account.AssignAccountRoleRequest;
import com.htttdn.hrm.dto.request.account.CreateAccountRequest;
import com.htttdn.hrm.dto.request.organization.CreateExecutiveEmployeeRequest;
import com.htttdn.hrm.dto.request.organization.CreateOrganizationExecutiveRequest;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organization.OrganizationExecutiveProvisioningResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeRepository;

@Service
@Transactional
public class OrganizationExecutiveProvisioningService {

    private static final String EXECUTIVE_APPROVER_ROLE = "EXECUTIVE_APPROVER";

    private final EmployeeRepository employeeRepository;
    private final AccountAdminService accountAdminService;
    private final AccountRoleAssignmentAdminService roleAssignmentAdminService;

    public OrganizationExecutiveProvisioningService(
        EmployeeRepository employeeRepository,
        AccountAdminService accountAdminService,
        AccountRoleAssignmentAdminService roleAssignmentAdminService
    ) {
        this.employeeRepository = employeeRepository;
        this.accountAdminService = accountAdminService;
        this.roleAssignmentAdminService = roleAssignmentAdminService;
    }

    @PreAuthorize("hasAuthority('organization.executive.provision')")
    public OrganizationExecutiveProvisioningResponse provision(CreateOrganizationExecutiveRequest request) {
        if (request.effectiveFrom().isBefore(request.employee().hireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must be on or after employee.hireDate",
                "effectiveFrom"
            );
        }
        Employee employee = createExecutiveEmployee(request.employee());

        AccountProvisioningResponse accountProvisioning = accountAdminService.create(
            new CreateAccountRequest(employee.getId(), request.account().username())
        );
        Long accountId = accountProvisioning.account().id();
        AccountRoleAssignmentResponse executiveAssignment = roleAssignmentAdminService.assign(
            accountId,
            new AssignAccountRoleRequest(
                EXECUTIVE_APPROVER_ROLE,
                RoleScopeType.COMPANY,
                null,
                null,
                request.effectiveFrom(),
                null,
                request.appointmentReason()
            )
        );

        return new OrganizationExecutiveProvisioningResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            accountProvisioning,
            executiveAssignment
        );
    }

    private Employee createExecutiveEmployee(CreateExecutiveEmployeeRequest request) {
        String employeeCode = request.employeeCode().trim();
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(employeeCode)) {
            throw new ConflictException(
                ErrorCode.EMPLOYEE_CODE_TAKEN,
                "Employee code is already taken",
                "employee.employeeCode"
            );
        }

        String workEmail = request.workEmail().trim().toLowerCase(Locale.ROOT);
        if (employeeRepository.existsByWorkEmailIgnoreCase(workEmail)) {
            throw new ConflictException(
                ErrorCode.EMAIL_TAKEN,
                "Work email is already taken",
                "employee.workEmail"
            );
        }

        Instant now = Instant.now();
        return employeeRepository.save(Employee.builder()
            .employeeCode(employeeCode)
            .fullName(request.fullName().trim())
            .workEmail(workEmail)
            .phone(normalizeNullable(request.phone()))
            .hireDate(request.hireDate())
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
}
