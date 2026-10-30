package com.htttdn.hrm.service.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.htttdn.hrm.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.List;

import com.htttdn.hrm.service.employee.ApproverResolver;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
import com.htttdn.hrm.dto.request.attendance.RecordPunchesRequest;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;
    @Mock private AttendanceCalendarService attendanceCalendarService;
    @Mock private ApproverResolver approverResolver;

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

        leaveService().applyApprovedLeave(leave);

        assertEquals(AttendanceStatus.PAID_LEAVE, record.getStatus());
        assertEquals(480, record.getPayableMinutes());
        assertEquals(leave, record.getLeaveRequest());
    }

    @Test
    void leaveMinutesCoverEachWorkDayOfThePeriodWithoutTheBreak() {
        WorkShift shift = WorkShift.builder().id(3L).startTime(LocalTime.of(8, 0)).breakMinutes(60)
            .breakStartTime(LocalTime.of(12, 0)).standardWorkMinutes(480).build();
        LocalDate monday = LocalDate.of(2026, 1, 12);
        when(attendanceCalendarService.scheduleFor(2L, monday.minusDays(1), monday.plusDays(2)))
            .thenReturn(List.of(officeDay(monday, shift), officeDay(monday.plusDays(1), shift), officeDay(monday.plusDays(2), shift)));

        // Monday 13:00 to Wednesday 12:00 (UTC+7).
        int minutes = leaveService().leaveMinutesFor(2L,
            Instant.parse("2026-01-12T06:00:00Z"), Instant.parse("2026-01-14T05:00:00Z"));

        assertEquals(240 + 480 + 240, minutes);
    }

    @Test
    void overtimeCannotReuseRegularPayableMinutes() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setCheckOutAt(Instant.now());
        record.setWorkedMinutes(480);
        record.setPayableMinutes(480);
        when(attendanceRecordRepository.getRequired(1L)).thenReturn(record);
        when(attendanceRecordRepository.getRequiredForUpdate(1L)).thenReturn(record);
        when(attendanceCalendarService.scheduleFor(2L, date, date)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(date, record.getShift(),
                record.getScheduledStartAt(), record.getScheduledEndAt(), 480)));
        stubApproverOf(record);

        BusinessException thrown = assertThrows(BusinessException.class, () -> overtimeService().approveOvertime(1L,
            new ApproveOvertimeRequest(60, new java.math.BigDecimal("1.5"))));
        assertEquals(ErrorCode.OVERTIME_EXCEEDS_EXTRA_TIME, thrown.getErrorCode());
    }

    @Test
    void overtimeOfSomeoneWhoHasLeftStillWaitsForTheirApprover() {
        Employee manager = Employee.builder().id(5L).build();
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(9L).employee(manager).build());
        AttendanceRecord leaversDay = record(LocalDate.of(2026, 1, 10));
        leaversDay.setCheckOutAt(Instant.now());
        when(attendanceRecordRepository.findOvertimeAwaitingDecision()).thenReturn(List.of(leaversDay));
        when(approverResolver.decidedBy(manager, List.of(leaversDay.getEmployee()))).thenReturn(List.of(2L));

        var pending = overtimeService().listOvertimeAwaitingDecision();

        assertEquals(1, pending.size());
    }

    @Test
    void holidayOvertimeRejectsMultiplierBelowThree() {
        LocalDate holiday = LocalDate.of(2026, 1, 1);
        AttendanceRecord record = record(holiday);
        record.setCheckOutAt(Instant.now());
        record.setWorkedMinutes(120);
        record.setPayableMinutes(0);
        when(attendanceRecordRepository.getRequired(1L)).thenReturn(record);
        when(attendanceRecordRepository.getRequiredForUpdate(1L)).thenReturn(record);
        when(attendanceCalendarService.isCompanyHoliday(holiday)).thenReturn(true);
        stubApproverOf(record);

        assertThrows(BusinessException.class, () -> overtimeService().approveOvertime(1L,
            new ApproveOvertimeRequest(120, new java.math.BigDecimal("2.0"))));
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
        // A Vietnamese sentence the web shows as is, naming who and which day lacks a schedule.
        assertEquals(ErrorCode.ATTENDANCE_SCHEDULE_MISSING, error.getErrorCode());
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("EMP002"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("15/01/2026"));
    }

    @Test
    void adjustingADayClearsItsOvertimeDecision() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setOvertimeMinutes(120);
        record.setOvertimeMultiplier(new java.math.BigDecimal("1.5"));
        record.setOvertimeApprovedAt(Instant.now());
        when(attendanceRecordRepository.getRequired(1L)).thenReturn(record);
        when(attendanceRecordRepository.getRequiredForUpdate(1L)).thenReturn(record);

        // Marked absent after the overtime was approved: no overtime may be paid for that day any more.
        service().adjust(1L, new AdjustAttendanceRequest(0, 0, 0, 0, AttendanceStatus.UNAUTHORIZED_ABSENCE, "Vắng"));

        assertEquals(0, record.getOvertimeMinutes());
        assertEquals(null, record.getOvertimeApprovedAt());
    }

    @Test
    void markingTheRestOfAHalfDayLeaveAbsentKeepsThePaidLeave() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setStatus(AttendanceStatus.MISSING_PUNCH);
        record.setCheckInAt(null);
        record.setLeaveRequest(LeaveRequest.builder().id(5L).salaryTreatment(LeaveSalaryTreatment.EMPLOYER_PAID).build());
        record.setLeaveMinutes(240);
        when(attendanceRecordRepository.getRequired(1L)).thenReturn(record);
        when(attendanceRecordRepository.getRequiredForUpdate(1L)).thenReturn(record);

        service().adjust(1L, new AdjustAttendanceRequest(0, 0, 0, 0, AttendanceStatus.UNAUTHORIZED_ABSENCE, "Chiều vắng"));

        assertEquals(AttendanceStatus.UNAUTHORIZED_ABSENCE, record.getStatus());
        assertEquals(240, record.getPayableMinutes());
    }

    @Test
    void leaveStatusesAreNotSetByHand() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setStatus(AttendanceStatus.MISSING_PUNCH);
        when(attendanceRecordRepository.getRequired(1L)).thenReturn(record);
        when(attendanceRecordRepository.getRequiredForUpdate(1L)).thenReturn(record);

        BusinessException thrown = assertThrows(BusinessException.class, () -> service().adjust(1L,
            new AdjustAttendanceRequest(0, 480, 0, 0, AttendanceStatus.PAID_LEAVE, "Nghỉ phép")));

        assertEquals("status", thrown.getField());
    }

    @Test
    void aFullDayOfApprovedLeaveIsNotAdjusted() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setStatus(AttendanceStatus.PAID_LEAVE);
        when(attendanceRecordRepository.getRequired(1L)).thenReturn(record);
        when(attendanceRecordRepository.getRequiredForUpdate(1L)).thenReturn(record);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service().adjust(1L,
            new AdjustAttendanceRequest(0, 0, 0, 0, AttendanceStatus.UNAUTHORIZED_ABSENCE, "Vắng")));

        assertEquals(ErrorCode.ATTENDANCE_FULL_DAY_LEAVE, thrown.getErrorCode());
    }

    @Test
    void aNewAssignmentGivesUntouchedPreparedDaysItsShiftAndDropsDaysWithoutOne() {
        LocalDate kept = LocalDate.of(2026, 1, 20);
        LocalDate dropped = LocalDate.of(2026, 1, 21);
        AttendanceRecord first = record(kept);
        AttendanceRecord second = record(dropped);
        WorkShift afternoon = WorkShift.builder().id(4L).build();
        when(attendanceRecordRepository.findUntouchedFromForUpdate(2L, kept)).thenReturn(List.of(first, second));
        when(attendanceCalendarService.scheduleFor(2L, kept, dropped)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(kept, afternoon,
                Instant.parse("2026-01-20T06:00:00Z"), Instant.parse("2026-01-20T14:00:00Z"), 420)));

        service().rescheduleUntouchedDays(2L, kept);

        assertEquals(afternoon, first.getShift());
        assertEquals(Instant.parse("2026-01-20T06:00:00Z"), first.getScheduledStartAt());
        assertEquals(420, first.getScheduledMinutes());
        verify(attendanceRecordRepository).delete(second);
    }

    @Test
    void adjustmentCannotResolveMissingPunchWithContradictoryPresentData() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setStatus(AttendanceStatus.MISSING_PUNCH);
        when(attendanceRecordRepository.getRequired(1L)).thenReturn(record);
        when(attendanceRecordRepository.getRequiredForUpdate(1L)).thenReturn(record);

        assertThrows(BusinessException.class, () -> service().adjust(1L,
            new AdjustAttendanceRequest(0, 480, 0, 0, AttendanceStatus.PRESENT, "Test")));
    }

    @Test
    void morningLeaveApprovedAfterAfternoonWorkCompletesTheDay() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        // 08:00-17:00 in Asia/Ho_Chi_Minh, break 12:00-13:00; worked 13:00-17:00.
        AttendanceRecord record = officeDay(date, "2026-01-15T06:00:00Z", "2026-01-15T10:00:00Z");
        LeaveRequest leave = leave(record, "2026-01-15T01:00:00Z", "2026-01-15T05:00:00Z");
        stubOfficeDay(record, date);

        leaveService().applyApprovedLeave(leave);

        assertEquals(240, record.getLeaveMinutes());
        assertEquals(0, record.getLateMinutes());
        assertEquals(0, record.getEarlyLeaveMinutes());
        assertEquals(240, record.getWorkedMinutes());
        assertEquals(480, record.getPayableMinutes());
        assertEquals(AttendanceStatus.PRESENT, record.getStatus());
    }

    @Test
    void leaveOverlappingWorkedTimeIsRefusedWithItsOwnCode() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        // Worked 08:00-12:00, yet the leave claims the same morning.
        AttendanceRecord record = officeDay(date, "2026-01-15T01:00:00Z", "2026-01-15T05:00:00Z");
        LeaveRequest leave = leave(record, "2026-01-15T01:00:00Z", "2026-01-15T05:00:00Z");
        stubLeaveDay(record, date);

        ConflictException thrown = assertThrows(ConflictException.class, () -> leaveService().applyApprovedLeave(leave));

        assertEquals(ErrorCode.LEAVE_OVERLAPS_WORKED_TIME, thrown.getErrorCode());
    }

    @Test
    void leaveOnADayStillMissingItsCheckOutIsRefusedWithItsOwnCode() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = officeDay(date, "2026-01-15T06:00:00Z", "2026-01-15T10:00:00Z");
        record.setCheckOutAt(null);
        LeaveRequest leave = leave(record, "2026-01-15T01:00:00Z", "2026-01-15T05:00:00Z");
        stubLeaveDay(record, date);

        ConflictException thrown = assertThrows(ConflictException.class, () -> leaveService().applyApprovedLeave(leave));

        assertEquals(ErrorCode.LEAVE_DAY_PUNCH_INCOMPLETE, thrown.getErrorCode());
    }

    @Test
    void leaveLongerThanTheCurrentScheduleIsRefusedWithItsOwnCode() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = officeDay(date, "2026-01-15T06:00:00Z", "2026-01-15T10:00:00Z");
        LeaveRequest leave = leave(record, "2026-01-15T01:00:00Z", "2026-01-15T05:00:00Z");
        // Filed when the morning was longer; the shift has changed since.
        leave.setRequestedMinutes(300);
        stubOfficeDay(record, date);

        BusinessException thrown = assertThrows(BusinessException.class, () -> leaveService().applyApprovedLeave(leave));

        assertEquals(ErrorCode.LEAVE_SCHEDULE_CHANGED, thrown.getErrorCode());
    }

    @Test
    void afternoonLeaveSpanningTheBreakCountsOnlyWorkMinutes() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        // Worked 08:00-12:00; leave filed 12:00-17:00 covers the break, which is not leave time.
        AttendanceRecord record = officeDay(date, "2026-01-15T01:00:00Z", "2026-01-15T05:00:00Z");
        LeaveRequest leave = leave(record, "2026-01-15T05:00:00Z", "2026-01-15T10:00:00Z");
        stubOfficeDay(record, date);

        leaveService().applyApprovedLeave(leave);

        assertEquals(240, record.getLeaveMinutes());
        assertEquals(0, record.getEarlyLeaveMinutes());
        assertEquals(480, record.getPayableMinutes());
    }

    @Test
    void recordPunchesCreatesTheDayAndDerivesMinutes() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        // 08:00-17:00 in Asia/Ho_Chi_Minh, break 12:00-13:00; punched 08:20-17:00 with 15 minutes grace.
        WorkShift shift = WorkShift.builder().id(3L).startTime(LocalTime.of(8, 0)).breakMinutes(60)
            .breakStartTime(LocalTime.of(12, 0)).standardWorkMinutes(480).graceLateMinutes(15).build();
        when(employeeRepository.getRequired(2L)).thenReturn(
            Employee.builder().id(2L).hireDate(LocalDate.of(2024, 1, 1)).build());
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, date)).thenReturn(Optional.empty());
        when(attendanceCalendarService.assignedDay(2L, date)).thenReturn(Optional.of(
            new AttendanceCalendarService.ScheduledDay(date, shift,
                Instant.parse("2026-01-15T01:00:00Z"), Instant.parse("2026-01-15T10:00:00Z"), 480)));
        when(attendanceCalendarService.companyWorkDates(date, date)).thenReturn(List.of(date));
        when(attendanceRecordRepository.save(org.mockito.ArgumentMatchers.any(AttendanceRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(7L).build());

        var response = service().recordPunches(new RecordPunchesRequest(2L, date,
            Instant.parse("2026-01-15T01:20:00Z"), Instant.parse("2026-01-15T10:00:00Z"), null));

        assertEquals(AttendanceStatus.PRESENT, response.status());
        assertEquals(5, response.lateMinutes());
        assertEquals(0, response.earlyLeaveMinutes());
        assertEquals(460, response.workedMinutes());
        assertEquals(460, response.payableMinutes());
    }

    @Test
    void recordPunchesOverwritesAnAdjustedDayAndClearsOvertime() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = officeDay(date, "2026-01-15T01:00:00Z", "2026-01-15T12:00:00Z");
        record.setStatus(AttendanceStatus.UNAUTHORIZED_ABSENCE);
        record.setOvertimeMinutes(120);
        record.setOvertimeMultiplier(new java.math.BigDecimal("1.5"));
        when(employeeRepository.getRequired(2L)).thenReturn(
            Employee.builder().id(2L).hireDate(LocalDate.of(2024, 1, 1)).build());
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, date)).thenReturn(Optional.of(record));
        when(attendanceCalendarService.companyWorkDates(date, date)).thenReturn(List.of(date));
        when(attendanceRecordRepository.save(record)).thenReturn(record);
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(7L).build());

        var response = service().recordPunches(new RecordPunchesRequest(2L, date,
            Instant.parse("2026-01-15T01:00:00Z"), Instant.parse("2026-01-15T10:00:00Z"), " Máy chấm gửi muộn "));

        assertEquals(AttendanceStatus.PRESENT, response.status());
        assertEquals(480, response.payableMinutes());
        assertEquals(0, response.overtimeMinutes());
        assertEquals("Máy chấm gửi muộn", record.getNote());
        assertEquals(7L, record.getUpdatedByAccount().getId());
    }

    @Test
    void recordPunchesRejectsAFullDayOfApprovedLeave() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        AttendanceRecord record = record(date);
        record.setCheckInAt(null);
        record.setStatus(AttendanceStatus.PAID_LEAVE);
        when(employeeRepository.getRequired(2L)).thenReturn(
            Employee.builder().id(2L).hireDate(LocalDate.of(2024, 1, 1)).build());
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, date)).thenReturn(Optional.of(record));

        assertThrows(ConflictException.class, () -> service().recordPunches(new RecordPunchesRequest(2L, date,
            Instant.parse("2026-01-15T01:00:00Z"), Instant.parse("2026-01-15T10:00:00Z"), null)));
    }

    @Test
    void recordPunchesRejectsCheckInOnAnotherDay() {
        LocalDate date = LocalDate.of(2026, 1, 15);

        assertThrows(BusinessException.class, () -> service().recordPunches(new RecordPunchesRequest(2L, date,
            Instant.parse("2026-01-16T01:00:00Z"), Instant.parse("2026-01-16T10:00:00Z"), null)));
    }

    private AttendanceRecord officeDay(LocalDate date, String checkIn, String checkOut) {
        return AttendanceRecord.builder()
            .id(1L)
            .employee(Employee.builder().id(2L).build())
            .workDate(date)
            .shift(WorkShift.builder().id(3L).startTime(LocalTime.of(8, 0)).breakMinutes(60)
                .breakStartTime(LocalTime.of(12, 0)).standardWorkMinutes(480).graceLateMinutes(15).build())
            .scheduledStartAt(Instant.parse("2026-01-15T01:00:00Z"))
            .scheduledEndAt(Instant.parse("2026-01-15T10:00:00Z"))
            .scheduledMinutes(480)
            .checkInAt(Instant.parse(checkIn))
            .checkOutAt(Instant.parse(checkOut))
            .workedMinutes(240)
            .payableMinutes(240)
            .status(AttendanceStatus.PRESENT)
            .build();
    }

    /** 08:00 to 17:00 in UTC+7. */
    private static AttendanceCalendarService.ScheduledDay officeDay(LocalDate date, WorkShift shift) {
        Instant start = date.atTime(1, 0).toInstant(java.time.ZoneOffset.UTC);
        return new AttendanceCalendarService.ScheduledDay(date, shift, start, start.plusSeconds(9 * 3600), 480);
    }

    private static LeaveRequest leave(AttendanceRecord record, String start, String end) {
        return LeaveRequest.builder().id(5L).employee(record.getEmployee())
            .startAt(Instant.parse(start)).endAt(Instant.parse(end)).requestedMinutes(240)
            .salaryTreatment(LeaveSalaryTreatment.EMPLOYER_PAID).leaveType(LeaveType.ANNUAL).build();
    }

    private void stubOfficeDay(AttendanceRecord record, LocalDate date) {
        stubLeaveDay(record, date);
        when(attendanceCalendarService.companyWorkDates(date, date)).thenReturn(List.of(date));
    }

    /** Just what applying a leave reads before it refuses the day. */
    private void stubLeaveDay(AttendanceRecord record, LocalDate date) {
        when(attendanceCalendarService.scheduleFor(2L, date.minusDays(1), date))
            .thenReturn(List.of(new AttendanceCalendarService.ScheduledDay(date, record.getShift(),
                record.getScheduledStartAt(), record.getScheduledEndAt(), 480)));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(2L, date))
            .thenReturn(Optional.of(record));
    }

    /** The signed-in account is the approver of the record's employee. */
    private void stubApproverOf(AttendanceRecord record) {
        Employee manager = Employee.builder().id(5L).build();
        when(currentAccountProvider.account()).thenReturn(Account.builder().id(9L).employee(manager).build());
        when(approverResolver.approverFor(record.getEmployee())).thenReturn(Optional.of(manager));
    }

    private AttendanceRecord record(LocalDate date) {
        Instant now = Instant.now();
        return AttendanceRecord.builder()
            .id(1L)
            .employee(Employee.builder().id(2L).build())
            .workDate(date)
            .shift(WorkShift.builder().id(3L).startTime(LocalTime.of(8, 0)).breakMinutes(60)
                .breakStartTime(LocalTime.of(10, 0)).standardWorkMinutes(480).graceLateMinutes(0).build())
            .scheduledStartAt(now.minusSeconds(180 * 60))
            .scheduledEndAt(now.plusSeconds(300 * 60))
            .scheduledMinutes(480)
            .checkInAt(now.minusSeconds(120 * 60))
            .status(AttendanceStatus.PRESENT)
            .build();
    }

    private AttendanceService service() {
        return new AttendanceService(attendanceRecordRepository, employeeRepository, currentAccountProvider,
            employeeAccessScopeService, attendanceCalendarService, editGuard(), new AttendanceMapper(),
            new AttendanceDayCalculator(attendanceCalendarService));
    }

    private LeaveAttendanceService leaveService() {
        return new LeaveAttendanceService(attendanceRecordRepository, attendanceCalendarService, editGuard(),
            new AttendanceDayCalculator(attendanceCalendarService));
    }

    private OvertimeService overtimeService() {
        return new OvertimeService(attendanceRecordRepository, currentAccountProvider,
            attendanceCalendarService, approverResolver, editGuard(), new AttendanceMapper(), Clock.system(ClockConfig.BUSINESS_ZONE));
    }

    private AttendanceEditGuard editGuard() {
        return new AttendanceEditGuard(attendanceRecordRepository, payrollPeriodRepository);
    }
}
