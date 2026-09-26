package com.htttdn.hrm.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organization.CreateHrStaffEmployeeRequest;
import com.htttdn.hrm.dto.request.organization.CreateOrganizationHrStaffRequest;
import com.htttdn.hrm.dto.response.account.AccountProvisioningResponse;
import com.htttdn.hrm.dto.response.account.AccountRoleAssignmentResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organization.OrganizationHrStaffProvisioningResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class OrganizationHrStaffProvisioningService {

    private static final String HR_STAFF_ROLE = "HR_STAFF";

    private final EmployeeProvisioningService employeeProvisioningService;
    private final AccountProvisioningService accountProvisioningService;
    private final AccountRoleAssignmentAdminService roleAssignmentAdminService;
    private final CurrentAccountProvider currentAccountProvider;

    public OrganizationHrStaffProvisioningService(
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

    @PreAuthorize("hasAuthority('organization.hr_staff.bootstrap')")
    public OrganizationHrStaffProvisioningResponse provision(CreateOrganizationHrStaffRequest request) {
        if (request.effectiveFrom().isBefore(request.employee().hireDate())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "effectiveFrom must be on or after employee.hireDate",
                "effectiveFrom"
            );
        }

        Long actorAccountId = currentAccountProvider.accountId();
        Employee employee = createHrStaffEmployee(request.employee());
        AccountProvisioningResponse accountProvisioning = accountProvisioningService.provisionPendingAccount(
            employee.getId(),
            request.account().username(),
            actorAccountId
        );
        AccountRoleAssignmentResponse hrStaffAssignment = roleAssignmentAdminService.assignProvisionedCompanyRole(
            accountProvisioning.account().id(),
            HR_STAFF_ROLE,
            request.effectiveFrom(),
            request.appointmentReason(),
            actorAccountId
        );

        return new OrganizationHrStaffProvisioningResponse(
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            accountProvisioning,
            hrStaffAssignment
        );
    }

    private Employee createHrStaffEmployee(CreateHrStaffEmployeeRequest request) {
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
