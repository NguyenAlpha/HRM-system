package com.htttdn.hrm.service.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.request.jobposition.CreateJobPositionRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;

@ExtendWith(MockitoExtension.class)
class JobPositionServiceTest {
    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @Test
    void aNewPositionIsTrimmedAndActive() {
        when(jobPositionRepository.saveAndFlush(any(JobPosition.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().create(new CreateJobPositionRequest(" DEV ", " Lập trình viên ", " ", false));

        assertEquals("DEV", result.code());
        assertEquals("Lập trình viên", result.title());
        assertNull(result.description());
        assertTrue(result.isActive());
    }

    @Test
    void aCodeAlreadyInUseIsRefusedEvenWhenTwoCreatesRace() {
        when(jobPositionRepository.existsByCodeAndDeletedAtIsNull("DEV")).thenReturn(true);
        ConflictException taken = assertThrows(ConflictException.class,
            () -> service().create(new CreateJobPositionRequest("DEV", "Dev", null, false)));
        assertEquals(ErrorCode.JOB_POSITION_CODE_TAKEN, taken.getErrorCode());
        assertEquals("code", taken.getField());

        // The other request saved first: the unique index answers instead of the check above.
        when(jobPositionRepository.existsByCodeAndDeletedAtIsNull("QA")).thenReturn(false);
        when(jobPositionRepository.saveAndFlush(any(JobPosition.class)))
            .thenThrow(new DataIntegrityViolationException("uq_job_positions_code"));
        ConflictException raced = assertThrows(ConflictException.class,
            () -> service().create(new CreateJobPositionRequest("QA", "QA", null, false)));
        assertEquals(ErrorCode.JOB_POSITION_CODE_TAKEN, raced.getErrorCode());
    }

    @Test
    void aPositionStillAssignedIsNotDeleted() {
        JobPosition position = JobPosition.builder().id(3L).code("DEV").title("Dev").isActive(true).build();
        when(jobPositionRepository.getRequiredForUpdate(3L)).thenReturn(position);
        when(employeeAssignmentRepository.existsCurrentOrFutureByPositionId(eq(3L), any())).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class, () -> service().softDelete(3L));

        assertEquals(ErrorCode.ORGANIZATION_RESOURCE_IN_USE, exception.getErrorCode());
        assertNull(position.getDeletedAt());
        assertTrue(position.getIsActive());
    }

    @Test
    void anUnusedPositionIsSoftDeletedAndDeactivated() {
        JobPosition position = JobPosition.builder().id(4L).code("OLD").title("Old").isActive(true).build();
        when(jobPositionRepository.getRequiredForUpdate(4L)).thenReturn(position);

        service().softDelete(4L);

        assertNotNull(position.getDeletedAt());
        assertFalse(position.getIsActive());
    }

    private JobPositionService service() {
        return new JobPositionService(jobPositionRepository, employeeAssignmentRepository, employeeAccessScopeService,
            Clock.system(ClockConfig.BUSINESS_ZONE));
    }
}
