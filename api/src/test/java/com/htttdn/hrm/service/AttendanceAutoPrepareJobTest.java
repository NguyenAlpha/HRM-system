package com.htttdn.hrm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeRepository;

@ExtendWith(MockitoExtension.class)
class AttendanceAutoPrepareJobTest {

    @Mock private AttendanceService attendanceService;
    @Mock private EmployeeRepository employeeRepository;

    @Test
    void employeeWithoutShiftDoesNotStopTheOthers() {
        when(employeeRepository.findEmployedDuring(any(), any())).thenReturn(List.of(
            Employee.builder().id(1L).build(), Employee.builder().id(2L).build()));
        when(attendanceService.autoPrepareEmployee(eq(1L), anyInt(), anyInt()))
            .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "missing primary assignment"));

        new AttendanceAutoPrepareJob(attendanceService, employeeRepository).prepareCurrentMonth();

        verify(attendanceService).autoPrepareEmployee(eq(2L), anyInt(), anyInt());
    }

    @Test
    void lockedPayrollPeriodStopsTheRun() {
        when(employeeRepository.findEmployedDuring(any(), any())).thenReturn(List.of(
            Employee.builder().id(1L).build(), Employee.builder().id(2L).build()));
        when(attendanceService.autoPrepareEmployee(eq(1L), anyInt(), anyInt()))
            .thenThrow(new ConflictException(ErrorCode.PAYROLL_PERIOD_LOCKED, "locked"));

        new AttendanceAutoPrepareJob(attendanceService, employeeRepository).prepareCurrentMonth();

        verify(attendanceService, never()).autoPrepareEmployee(eq(2L), anyInt(), anyInt());
    }

    @Test
    void repositoryFailureDoesNotEscape() {
        when(employeeRepository.findEmployedDuring(any(), any())).thenThrow(new IllegalStateException("db down"));

        new AttendanceAutoPrepareJob(attendanceService, employeeRepository).prepareCurrentMonth();

        verify(attendanceService, never()).autoPrepareEmployee(anyLong(), anyInt(), anyInt());
    }
}
