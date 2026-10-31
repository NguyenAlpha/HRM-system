package com.htttdn.hrm.service.organization;

import com.htttdn.hrm.service.rbac.AccountRoleAssignmentCommandService;
import com.htttdn.hrm.service.rbac.AccountRoleAssignmentMapper;
import com.htttdn.hrm.service.employee.EmployeeProvisioningService;
import com.htttdn.hrm.service.account.AccountProvisioningService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.CreateCompanyOwnerEmployeeRequest;
import com.htttdn.hrm.dto.request.organization.CreateOrganizationCompanyOwnerRequest;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.organization.OrganizationCompanyOwnerProvisioningResponse;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.SystemRoles;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrganizationCompanyOwnerProvisioningService {

    private final EmployeeProvisioningService employeeProvisioningService;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountRoleAssignmentCommandService roleAssignmentCommandService;
    private final AccountRoleAssignmentMapper roleAssignmentMapper;
    private final CurrentAccountProvider currentAccountProvider;
    private final CompanyProfileService companyProfileService;

    @PreAuthorize("hasAuthority('organization.company_owner.bootstrap')")
    public OrganizationCompanyOwnerProvisioningResponse provision(
        CreateOrganizationCompanyOwnerRequest request
    ) {
        Account actor = currentAccountProvider.account();
        companyProfileService.save(request.company());
        Employee employee = createCompanyOwnerEmployee(request.employee());
        AccountProvisioningResponse accountProvisioning = accountProvisioningService.provisionPendingAccount(
            employee.getId(),
            request.account().username(),
            actor
        );
        AccountRoleAssignmentResponse ownerAssignment = roleAssignmentMapper.toResponse(
            roleAssignmentCommandService.assignProvisionedCompanyRole(
                accountProvisioning.account().id(),
                SystemRoles.COMPANY_OWNER,
                request.effectiveFrom(),
                request.ownershipReason(),
                actor
            )
        );
        AccountRoleAssignmentResponse directorAssignment = roleAssignmentMapper.toResponse(
            roleAssignmentCommandService.assignProvisionedCompanyRole(
                accountProvisioning.account().id(),
                SystemRoles.DIRECTOR,
                request.effectiveFrom(),
                request.directorAppointmentReason(),
                actor
            )
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
                request.fullName(),
                request.workEmail(),
                request.phone(),
                request.hireDate(),
                "employee.workEmail"
            )
        );
    }
}
