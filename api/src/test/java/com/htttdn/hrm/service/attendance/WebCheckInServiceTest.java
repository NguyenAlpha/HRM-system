package com.htttdn.hrm.service.attendance;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.CompanyProfileRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebCheckInServiceTest {

    // 08:20 in Vietnam on Thursday 15/01/2026; the shift starts at 08:00 with 5 minutes' grace.
    private static final Instant NOW = Instant.parse("2026-01-15T01:20:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    @Mock private CompanyProfileRepository companyProfileRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private AttendanceCalendarService attendanceCalendarService;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    private final Employee employee = Employee.builder().id(2L).hireDate(LocalDate.of(2025, 1, 1)).build();
    private final WorkShift shift = WorkShift.builder().id(3L).startTime(LocalTime.of(8, 0)).breakMinutes(60)
        .breakStartTime(LocalTime.of(12, 0)).standardWorkMinutes(480).graceLateMinutes(5).crossesMidnight(false).build();

    @BeforeEach
    void setUp() {
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(9L).employee(employee).build());
        when(attendanceRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attendanceCalendarService.assignedDay(2L, TODAY)).thenReturn(Optional.of(new AttendanceCalendarService.ScheduledDay(
            TODAY, shift, Instant.parse("2026-01-15T01:00:00Z"), Instant.parse("2026-01-15T10:00:00Z"), 480)));
        when(attendanceCalendarService.companyWorkDates(TODAY, TODAY)).thenReturn(List.of(TODAY));
        enabled(true);
    }

    @Test
    void checkInIsRefusedWhileWebCheckInIsOff() {
        enabled(false);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service(NOW).checkIn());

        assertEquals(ErrorCode.ATTENDANCE_WEB_CHECK_IN_DISABLED, thrown.getErrorCode());
    }

    @Test
    void checkInRecordsTheServerTimeAndCountsLatenessOnly() {
        var response = service(NOW).checkIn();

        assertEquals(NOW, response.checkInAt());
        assertNull(response.checkOutAt());
        assertEquals(15, response.lateMinutes());
        assertEquals(AttendanceStatus.MISSING_PUNCH, response.status());
    }

    @Test
    void checkingInTwiceIsAConflict() {
        AttendanceRecord record = AttendanceDayCalculator.newRecord(employee, attendanceCalendarService.assignedDay(2L, TODAY).orElseThrow());
        record.setCheckInAt(NOW);
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, TODAY)).thenReturn(Optional.of(record));

        ConflictException thrown = assertThrows(ConflictException.class, () -> service(NOW).checkIn());

        assertEquals(ErrorCode.ATTENDANCE_ALREADY_CHECKED_IN, thrown.getErrorCode());
    }

    @Test
    void checkOutBeforeCheckingInIsAConflict() {
        ConflictException thrown = assertThrows(ConflictException.class, () -> service(NOW).checkOut());

        assertEquals(ErrorCode.ATTENDANCE_NOT_CHECKED_IN, thrown.getErrorCode());
    }

    @Test
    void checkOutCompletesTheDay() {
        AttendanceRecord record = AttendanceDayCalculator.newRecord(employee, attendanceCalendarService.assignedDay(2L, TODAY).orElseThrow());
        record.setCheckInAt(Instant.parse("2026-01-15T01:00:00Z"));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, TODAY)).thenReturn(Optional.of(record));

        // 17:00 in Vietnam: the whole shift, less the hour's break.
        var response = service(Instant.parse("2026-01-15T10:00:00Z")).checkOut();

        assertEquals(AttendanceStatus.PRESENT, response.status());
        assertEquals(480, response.payableMinutes());
        assertEquals(0, response.earlyLeaveMinutes());
    }

    private void enabled(boolean on) {
        when(companyProfileRepository.findById((short) 1))
            .thenReturn(Optional.of(CompanyProfile.builder().id((short) 1).webCheckInEnabled(on).build()));
    }

    private WebCheckInService service(Instant now) {
        return new WebCheckInService(companyProfileRepository, attendanceRecordRepository, attendanceCalendarService,
            new AttendanceEditGuard(attendanceRecordRepository, payrollPeriodRepository),
            new AttendanceDayCalculator(attendanceCalendarService), new AttendanceMapper(), currentAccountProvider,
            employeeAccessScopeService, Clock.fixed(now, ClockConfig.BUSINESS_ZONE));
    }
}
