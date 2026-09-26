package com.htttdn.hrm.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.CreateDirectorEmployeeRequest;
import com.htttdn.hrm.dto.request.organization.CreateOrganizationDirectorRequest;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organization.OrganizationDirectorProvisioningResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class OrganizationDirectorProvisioningService {

    private static final String DIRECTOR_ROLE = "DIRECTOR";

    private final EmployeeProvisioningService employeeProvisioningService;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountRoleAssignmentAdminService roleAssignmentAdminService;
    private final CurrentAccountProvider currentAccountProvider;

    public OrganizationDirectorProvisioningService(
        EmployeeProvisioningService employeeProvisioningService,
        AccountProvisioningService accountProvisioningService,
        AccountRoleAssignmentAdminService roleAssignmentAdminService,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.employeeProvisioningService = employeeProvisioningService;
        this.accountProvisioningService = accountProvisioningService;
        this.roleAssignmentAdminService = roleAssignmentAdminService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PreAuthorize("hasAuthority('organization.director.provision')")
    public OrganizationDirectorProvisioningResponse provision(CreateOrganizationDirectorRequest request) {
        if (request.effectiveFrom().isBefore(request.employee().hireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must be on or after employee.hireDate",
                "effectiveFrom"
            );
        }
        Long actorAccountId = currentAccountProvider.accountId();
        Employee employee = createDirectorEmployee(request.employee());

        AccountProvisioningResponse accountProvisioning = accountProvisioningService.provisionPendingAccount(
            employee.getId(),
            request.account().username(),
            actorAccountId
        );
        Long accountId = accountProvisioning.account().id();
        AccountRoleAssignmentResponse directorAssignment = roleAssignmentAdminService.assignProvisionedCompanyRole(
            accountId,
            DIRECTOR_ROLE,
            request.effectiveFrom(),
            request.appointmentReason(),
            actorAccountId
        );

        return new OrganizationDirectorProvisioningResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            accountProvisioning,
            directorAssignment
        );
    }

    private Employee createDirectorEmployee(CreateDirectorEmployeeRequest request) {
        return employeeProvisioningService.createActiveMinimalEmployee(
            new EmployeeProvisioningService.MinimalEmployeeCommand(
                request.employeeCode(),
                request.fullName(),
                request.workEmail(),
                request.phone(),
                request.hireDate(),
                "employee.employeeCode",
                "employee.workEmail"
            )
        );
    }
}
