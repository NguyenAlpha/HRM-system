package com.htttdn.hrm.service.organization;

import java.time.Clock;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.request.workshift.CreateWorkShiftRequest;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkShiftServiceTest {
    @Mock private WorkShiftRepository workShiftRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
        // break minutes without a start, and a start without break minutes
        "08:00, 17:00, false, 60, NULL",
        "08:00, 17:00, false, 0, 12:00",
        // break starting at the shift start, before it, or running past its end
        "08:00, 17:00, false, 60, 08:00",
        "08:00, 17:00, false, 60, 07:00",
        "08:00, 17:00, false, 60, 16:30",
        // overnight shift whose break spills past the end the next morning
        "22:00, 06:00, true, 60, 05:30"
    })
    void createRejectsBreakOutsideTheShift(String start, String end, boolean overnight, int breakMinutes,
        String breakStart) {
        var request = request(start, end, overnight, breakMinutes, breakStart);

        BusinessException exception = assertThrows(BusinessException.class, () -> service().create(request));
        assertEquals("breakStartTime", exception.getField());
    }

    @Test
    void createAcceptsBreakAfterMidnightInAnOvernightShift() {
        when(workShiftRepository.saveAndFlush(any(WorkShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().create(request("22:00", "06:00", true, 30, "02:00"));

        assertEquals(LocalTime.of(2, 0), result.breakStartTime());
    }

    private static CreateWorkShiftRequest request(String start, String end, boolean overnight, int breakMinutes,
        String breakStart) {
        return new CreateWorkShiftRequest("SHIFT_T", "Test shift", LocalTime.parse(start), LocalTime.parse(end),
            breakMinutes, breakStart == null ? null : LocalTime.parse(breakStart), 420, 0, overnight);
    }

    private WorkShiftService service() {
        return new WorkShiftService(workShiftRepository, employeeAssignmentRepository, attendanceRecordRepository,
            Clock.system(ClockConfig.BUSINESS_ZONE));
    }
}
