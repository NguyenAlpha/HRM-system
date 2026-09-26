package com.htttdn.hrm.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.CreateCompanyOwnerEmployeeRequest;
import com.htttdn.hrm.dto.request.organization.CreateOrganizationCompanyOwnerRequest;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organization.OrganizationCompanyOwnerProvisioningResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class OrganizationCompanyOwnerProvisioningService {

    private static final String COMPANY_OWNER_ROLE = "COMPANY_OWNER";
    private static final String DIRECTOR_ROLE = "DIRECTOR";

    private final EmployeeProvisioningService employeeProvisioningService;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountRoleAssignmentAdminService roleAssignmentAdminService;
    private final CurrentAccountProvider currentAccountProvider;

    public OrganizationCompanyOwnerProvisioningService(
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

    @PreAuthorize("hasAuthority('organization.company_owner.bootstrap')")
    public OrganizationCompanyOwnerProvisioningResponse provision(
        CreateOrganizationCompanyOwnerRequest request
    ) {
        if (request.effectiveFrom().isBefore(request.employee().hireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must be on or after employee.hireDate",
                "effectiveFrom"
            );
        }

        Long actorAccountId = currentAccountProvider.accountId();
        Employee employee = createCompanyOwnerEmployee(request.employee());
        AccountProvisioningResponse accountProvisioning = accountProvisioningService.provisionPendingAccount(
            employee.getId(),
            request.account().username(),
            actorAccountId
        );
        AccountRoleAssignmentResponse ownerAssignment = roleAssignmentAdminService.assignProvisionedCompanyRole(
            accountProvisioning.account().id(),
            COMPANY_OWNER_ROLE,
            request.effectiveFrom(),
            request.ownershipReason(),
            actorAccountId
        );
        AccountRoleAssignmentResponse directorAssignment = roleAssignmentAdminService.assignProvisionedCompanyRole(
            accountProvisioning.account().id(),
            DIRECTOR_ROLE,
            request.effectiveFrom(),
            request.directorAppointmentReason(),
            actorAccountId
        );

        return new OrganizationCompanyOwnerProvisioningResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            accountProvisioning,
            ownerAssignment,
            directorAssignment
        );
    }

    private Employee createCompanyOwnerEmployee(CreateCompanyOwnerEmployeeRequest request) {
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
