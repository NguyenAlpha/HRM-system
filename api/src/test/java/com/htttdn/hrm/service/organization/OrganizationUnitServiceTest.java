package com.htttdn.hrm.service.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.request.organizationunit.UpdateOrganizationUnitRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.enums.OrganizationUnitType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationUnitServiceTest {
    @Mock private OrganizationUnitRepository organizationUnitRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    @Mock private RoleAssignmentRequestRepository roleAssignmentRequestRepository;

    private final OrganizationUnit inactiveBoard = OrganizationUnit.builder().id(1L).code("B")
        .name("Board").unitType(OrganizationUnitType.BOARD).isActive(false).build();
    private final OrganizationUnit inactiveDepartment = OrganizationUnit.builder().id(2L).code("D")
        .name("Department").unitType(OrganizationUnitType.DEPARTMENT).parentUnit(inactiveBoard).isActive(false).build();

    @Test
    void inactiveUnitUnderInactiveParentCanBeRenamed() {
        when(organizationUnitRepository.getRequiredForUpdate(2L)).thenReturn(inactiveDepartment);
        when(organizationUnitRepository.getRequired(1L)).thenReturn(inactiveBoard);
        when(organizationUnitRepository.saveAndFlush(any(OrganizationUnit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().update(2L, new UpdateOrganizationUnitRequest(1L, "Renamed", false));

        assertEquals("Renamed", result.name());
    }

    @Test
    void unitCannotBeActivatedUnderInactiveParent() {
        when(organizationUnitRepository.getRequiredForUpdate(2L)).thenReturn(inactiveDepartment);
        when(organizationUnitRepository.getRequired(1L)).thenReturn(inactiveBoard);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().update(2L, new UpdateOrganizationUnitRequest(1L, "Department", true)));
        assertEquals(ErrorCode.ORGANIZATION_HIERARCHY_INVALID, exception.getErrorCode());
    }

    private OrganizationUnitService service() {
        return new OrganizationUnitService(organizationUnitRepository, employeeAssignmentRepository,
            accountRoleAssignmentRepository, roleAssignmentRequestRepository, Clock.system(ClockConfig.BUSINESS_ZONE));
    }
}
