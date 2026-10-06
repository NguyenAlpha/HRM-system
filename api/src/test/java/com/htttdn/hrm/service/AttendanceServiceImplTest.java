package com.htttdn.hrm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.attendance.CheckOutRequest;
import com.htttdn.hrm.dto.request.attendance.CheckInRequest;
import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.impl.AttendanceServiceImpl;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;
    @Mock private AttendanceCalendarService attendanceCalendarService;

    @Test
    void approvedPaidLeaveUpdatesPayableMinutes() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setCheckInAt(null);
        record.setPayableMinutes(0);
        record.setStatus(AttendanceStatus.MISSING_PUNCH);
        Instant start = Instant.parse("2026-01-15T01:00:00Z");
        Instant end = Instant.parse("2026-01-15T10:00:00Z");
        LeaveRequest leave = LeaveRequest.builder().id(5L).employee(record.getEmployee())
            .startAt(start).endAt(end).requestedMinutes(480)
            .salaryTreatment(LeaveSalaryTreatment.EMPLOYER_PAID).leaveType(LeaveType.ANNUAL).build();
        when(attendanceCalendarService.scheduleFor(2L, date.minusDays(1), date))
            .thenReturn(List.of(new AttendanceCalendarService.ScheduledDay(date,
                record.getShift(), start, end, 480)));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, date))
            .thenReturn(Optional.of(record));

        service().applyApprovedLeave(leave);

        assertEquals(AttendanceStatus.PAID_LEAVE, record.getStatus());
        assertEquals(480, record.getPayableMinutes());
        assertEquals(leave, record.getLeaveRequest());
    }

    @Test
    void overtimeCannotReuseRegularPayableMinutes() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setCheckOutAt(Instant.now());
        record.setWorkedMinutes(480);
        record.setPayableMinutes(480);
        when(attendanceRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(attendanceRecordRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
        when(attendanceCalendarService.scheduleFor(2L, date, date)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(date, record.getShift(),
                record.getScheduledStartAt(), record.getScheduledEndAt(), 480)));

        assertThrows(BusinessException.class, () -> service().approveOvertime(1L,
            new ApproveOvertimeRequest(60, new java.math.BigDecimal("1.5"))));
    }

    @Test
    void prepareMonthCreatesMissingPunchForScheduledDay() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Employee employee = Employee.builder().id(2L).employeeCode("EMP002")
            .hireDate(LocalDate.of(2024, 1, 1)).build();
        WorkShift shift = WorkShift.builder().id(3L).build();
        when(employeeRepository.findEmployedDuring(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(List.of(employee));
        when(attendanceCalendarService.companyWorkDates(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).thenReturn(List.of(date));
        when(attendanceCalendarService.scheduleFor(2L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(List.of(new AttendanceCalendarService.ScheduledDay(date, shift,
                Instant.parse("2026-01-15T01:00:00Z"), Instant.parse("2026-01-15T10:00:00Z"), 480)));

        assertEquals(1, service().prepareMonth(2026, 1));

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository).save(captor.capture());
        assertEquals(AttendanceStatus.MISSING_PUNCH, captor.getValue().getStatus());
        assertEquals(480, captor.getValue().getScheduledMinutes());
    }

    @Test
    void prepareMonthReportsAssignmentGapForEmployedEmployee() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        Employee employee = Employee.builder().id(2L).employeeCode("EMP002")
            .hireDate(LocalDate.of(2024, 1, 1)).build();
        when(employeeRepository.findEmployedDuring(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(List.of(employee));
        when(attendanceCalendarService.scheduleFor(2L,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).thenReturn(List.of());
        when(attendanceCalendarService.companyWorkDates(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).thenReturn(List.of(date));

        BusinessException error = assertThrows(BusinessException.class,
            () -> service().prepareMonth(2026, 1));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("EMP002"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains(date.toString()));
    }

    @Test
    void adjustmentCannotResolveMissingPunchWithContradictoryPresentData() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setStatus(AttendanceStatus.MISSING_PUNCH);
        when(attendanceRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(attendanceRecordRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(record));

        assertThrows(BusinessException.class, () -> service().adjust(1L,
            new AdjustAttendanceRequest(0, 480, 0, 0, AttendanceStatus.PRESENT, "Test")));
    }

    @Test
    void checkInUsesExistingMissingPunchRecord() {
        LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        AttendanceRecord record = record(date);
        record.setCheckInAt(null);
        record.setStatus(AttendanceStatus.MISSING_PUNCH);
        record.getShift().setGraceLateMinutes(0);
        when(currentAccountProvider.hasAuthority("attendance.self.record")).thenReturn(true);
        when(currentAccountProvider.accountId()).thenReturn(7L);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(
            Account.builder().employee(record.getEmployee()).build()));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(record.getEmployee()));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, date))
            .thenReturn(Optional.of(record));

        var result = service().checkIn(new CheckInRequest(2L));

        assertEquals(AttendanceStatus.PRESENT, result.status());
        assertEquals(record.getCheckInAt(), result.checkInAt());
    }

    @Test
    void checkOutSubtractsBreakAndRejectsDuplicate() {
        LocalDate date = LocalDate.now();
        AttendanceRecord record = record(date);
        when(currentAccountProvider.hasAuthority("attendance.self.record")).thenReturn(true);
        when(currentAccountProvider.accountId()).thenReturn(7L);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(
            Account.builder().employee(record.getEmployee()).build()));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, date))
            .thenReturn(Optional.of(record));

        var result = service().checkOut(new CheckOutRequest(2L, date));

        assertEquals(60, result.workedMinutes());
        assertEquals(60, result.payableMinutes());
        assertThrows(ConflictException.class, () -> service().checkOut(new CheckOutRequest(2L, date)));
    }

    @Test
    void approvedPayrollBlocksCheckOut() {
        LocalDate date = LocalDate.now();
        AttendanceRecord record = record(date);
        when(currentAccountProvider.hasAuthority("attendance.self.record")).thenReturn(true);
        when(currentAccountProvider.accountId()).thenReturn(7L);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(
            Account.builder().employee(record.getEmployee()).build()));
        when(payrollPeriodRepository.findContainingDateForUpdate(date)).thenReturn(Optional.of(
            PayrollPeriod.builder().id(10L).status(PayrollPeriodStatus.APPROVED).build()));

        assertThrows(ConflictException.class, () -> service().checkOut(new CheckOutRequest(2L, date)));
    }

    private AttendanceRecord record(LocalDate date) {
        Instant now = Instant.now();
        return AttendanceRecord.builder()
            .id(1L)
            .employee(Employee.builder().id(2L).build())
            .workDate(date)
            .shift(WorkShift.builder().id(3L).breakMinutes(60).standardWorkMinutes(480).build())
            .scheduledStartAt(now.minusSeconds(180 * 60))
            .scheduledEndAt(now.plusSeconds(300 * 60))
            .scheduledMinutes(480)
            .checkInAt(now.minusSeconds(120 * 60))
            .status(AttendanceStatus.PRESENT)
            .build();
    }

    private AttendanceServiceImpl service() {
        return new AttendanceServiceImpl(attendanceRecordRepository, employeeRepository,
            employeeAssignmentRepository, accountRepository, payrollPeriodRepository,
            currentAccountProvider, employeeAccessScopeService, attendanceCalendarService);
    }
}
