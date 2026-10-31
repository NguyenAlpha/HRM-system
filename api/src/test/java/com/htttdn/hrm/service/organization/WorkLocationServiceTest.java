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
import com.htttdn.hrm.dto.request.worklocation.UpdateWorkLocationRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.LocationType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;

@ExtendWith(MockitoExtension.class)
class WorkLocationServiceTest {
    @Mock private WorkLocationRepository workLocationRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    @Mock private RoleAssignmentRequestRepository roleAssignmentRequestRepository;

    private final WorkLocation inactiveHeadOffice = WorkLocation.builder().id(1L).code("HO").name("Head office")
        .locationType(LocationType.HEAD_OFFICE).address("1 Street").isActive(false).build();
    private final WorkLocation inactiveBranch = WorkLocation.builder().id(2L).code("BR").name("Branch")
        .locationType(LocationType.BRANCH).address("2 Street").parentLocation(inactiveHeadOffice).isActive(false).build();

    @Test
    void inactiveLocationUnderInactiveParentCanBeEdited() {
        when(workLocationRepository.getRequiredForUpdate(2L)).thenReturn(inactiveBranch);
        when(workLocationRepository.getRequired(1L)).thenReturn(inactiveHeadOffice);
        when(workLocationRepository.saveAndFlush(any(WorkLocation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().update(2L, new UpdateWorkLocationRequest(1L, "Renamed", "3 Street", null, false));

        assertEquals("Renamed", result.name());
    }

    @Test
    void locationCannotBeActivatedUnderInactiveParent() {
        when(workLocationRepository.getRequiredForUpdate(2L)).thenReturn(inactiveBranch);
        when(workLocationRepository.getRequired(1L)).thenReturn(inactiveHeadOffice);

        BusinessException exception = assertThrows(BusinessException.class,
            () -> service().update(2L, new UpdateWorkLocationRequest(1L, "Branch", "2 Street", null, true)));
        assertEquals(ErrorCode.ORGANIZATION_HIERARCHY_INVALID, exception.getErrorCode());
    }

    private WorkLocationService service() {
        return new WorkLocationService(workLocationRepository, employeeAssignmentRepository,
            accountRoleAssignmentRepository, roleAssignmentRequestRepository, Clock.system(ClockConfig.BUSINESS_ZONE));
    }
}
