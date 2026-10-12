package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.request.attendance.RecordPunchesRequest;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class AttendanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceCalendarService attendanceCalendarService;

    public AttendanceService(
        AttendanceRecordRepository attendanceRecordRepository,
        EmployeeRepository employeeRepository,
        AccountRepository accountRepository,
        PayrollPeriodRepository payrollPeriodRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService,
        AttendanceCalendarService attendanceCalendarService
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.attendanceCalendarService = attendanceCalendarService;
    }

    @PreAuthorize("hasAuthority('attendance.manage')")
    public int prepareMonth(int year, int month) {
        employeeAccessScopeService.requireCompanyWide("attendance.manage");
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        assertAttendanceEditable(from);
        int created = 0;
        for (Employee employee : employeeRepository.findEmployedDuring(from, to)) {
            LocalDate employedFrom = employee.getHireDate().isAfter(from) ? employee.getHireDate() : from;
            LocalDate employedTo = employee.getTerminationDate() != null
                && employee.getTerminationDate().isBefore(to) ? employee.getTerminationDate() : to;
            List<AttendanceCalendarService.ScheduledDay> scheduledDays;
            try {
                scheduledDays = attendanceCalendarService.scheduleFor(employee.getId(), employedFrom, employedTo);
            } catch (BusinessException error) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Cannot prepare attendance for employee " + employee.getEmployeeCode()
                        + " (ID " + employee.getId() + "): " + error.getMessage());
            }
            Set<LocalDate> scheduledDates = scheduledDays.stream()
                .map(AttendanceCalendarService.ScheduledDay::workDate).collect(Collectors.toSet());
            for (LocalDate date : attendanceCalendarService.companyWorkDates(employedFrom, employedTo)) {
                if (!scheduledDates.contains(date)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Cannot prepare attendance for employee " + employee.getEmployeeCode()
                            + " (ID " + employee.getId() + "): missing primary assignment and work shift on " + date);
                }
            }
            Set<LocalDate> existingDates = attendanceRecordRepository
                .findByEmployeeIdAndWorkDateBetween(employee.getId(), from, to).stream()
                .map(AttendanceRecord::getWorkDate).collect(Collectors.toSet());
            for (AttendanceCalendarService.ScheduledDay day : scheduledDays) {
                if (existingDates.contains(day.workDate())) {
                    continue;
                }
                Instant now = Instant.now();
                attendanceRecordRepository.save(AttendanceRecord.builder()
                    .employee(employee)
                    .workDate(day.workDate())
                    .shift(day.shift())
                    .scheduledStartAt(day.startAt())
                    .scheduledEndAt(day.endAt())
                    .scheduledMinutes(day.minutes())
                    .workedMinutes(0)
                    .payableMinutes(0)
                    .lateMinutes(0)
                    .earlyLeaveMinutes(0)
                    .overtimeMinutes(0)
                    .overtimeMultiplier(java.math.BigDecimal.ONE)
                    .status(AttendanceStatus.MISSING_PUNCH)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
                created++;
            }
        }
        return created;
    }

    /**
     * Records one day's check-in and check-out with explicit timestamps, the way a time clock
     * reports them, and derives late, early-leave, worked and payable minutes from them.
     */
    @PreAuthorize("hasAuthority('attendance.manage')")
    public AttendanceRecordResponse recordPunches(RecordPunchesRequest request) {
        employeeAccessScopeService.requireEmployeeAccess(request.employeeId(), "attendance.manage");
        LocalDate workDate = request.workDate();
        Instant in = request.checkInAt();
        Instant out = request.checkOutAt();
        if (!LocalDate.ofInstant(in, ZONE).equals(workDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Check-in must fall on the work date " + workDate, "checkInAt");
        }
        if (!out.isAfter(in) || Duration.between(in, out).compareTo(Duration.ofDays(1)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Check-out must be after check-in and within 24 hours", "checkOutAt");
        }
        Employee employee = employeeRepository.findById(request.employeeId())
            .filter(e -> e.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + request.employeeId()));
        if (employee.getHireDate().isAfter(workDate)
            || employee.getTerminationDate() != null && employee.getTerminationDate().isBefore(workDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee is not employed on " + workDate, "workDate");
        }
        assertAttendanceEditable(workDate);

        AttendanceRecord record = attendanceRecordRepository
            .findByEmployeeIdAndWorkDateForUpdate(employee.getId(), workDate).orElse(null);
        if (record != null && (record.getCheckInAt() != null || record.getStatus() != AttendanceStatus.MISSING_PUNCH)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Attendance on " + workDate + " is already recorded");
        }
        if (record != null && record.getLeaveRequest() != null
            && in.isBefore(record.getLeaveRequest().getEndAt()) && out.isAfter(record.getLeaveRequest().getStartAt())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Worked time overlaps approved leave");
        }
        Instant now = Instant.now();
        if (record == null) {
            AttendanceCalendarService.ScheduledDay day = attendanceCalendarService
                .assignedDay(employee.getId(), workDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Employee has no primary assignment on " + workDate, "workDate"));
            record = AttendanceRecord.builder()
                .employee(employee)
                .workDate(workDate)
                .shift(day.shift())
                .scheduledStartAt(day.startAt())
                .scheduledEndAt(day.endAt())
                .scheduledMinutes(day.minutes())
                .workedMinutes(0)
                .payableMinutes(0)
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .overtimeMinutes(0)
                .overtimeMultiplier(java.math.BigDecimal.ONE)
                .createdAt(now)
                .build();
        }
        record.setCheckInAt(in);
        record.setCheckOutAt(out);
        applyPunches(record);
        record.setStatus(AttendanceStatus.PRESENT);
        record.setUpdatedAt(now);
        return toResponse(attendanceRecordRepository.save(record));
    }

    public void applyApprovedLeave(LeaveRequest request) {
        Long employeeId = request.getEmployee().getId();
        LocalDate from = LocalDate.ofInstant(request.getStartAt(), ZONE).minusDays(1);
        LocalDate to = LocalDate.ofInstant(request.getEndAt().minusNanos(1), ZONE);
        int remainingMinutes = request.getRequestedMinutes();
        for (AttendanceCalendarService.ScheduledDay day : attendanceCalendarService.scheduleFor(employeeId, from, to)) {
            Instant overlapStart = request.getStartAt().isAfter(day.startAt()) ? request.getStartAt() : day.startAt();
            Instant overlapEnd = request.getEndAt().isBefore(day.endAt()) ? request.getEndAt() : day.endAt();
            int overlapMinutes = workMinutes(day.startAt(), day.endAt(), day.shift(),
                request.getStartAt(), request.getEndAt());
            int leaveMinutes = Math.min(remainingMinutes, Math.min(day.minutes(), overlapMinutes));
            if (leaveMinutes <= 0) {
                continue;
            }
            assertAttendanceEditable(day.workDate());
            AttendanceRecord record = attendanceRecordRepository
                .findByEmployeeIdAndWorkDateForUpdate(employeeId, day.workDate()).orElse(null);
            if (record != null && record.getLeaveRequest() != null) {
                if (record.getLeaveRequest().getId().equals(request.getId())) {
                    remainingMinutes -= leaveMinutes;
                    continue;
                }
                throw new ConflictException(ErrorCode.CONFLICT,
                    "Attendance already references another leave request on " + day.workDate());
            }
            if (record != null && record.getCheckInAt() != null && record.getCheckOutAt() == null) {
                throw new ConflictException(ErrorCode.CONFLICT, "Complete attendance before approving leave on " + day.workDate());
            }
            if (record != null && record.getCheckInAt() != null && record.getCheckOutAt() != null
                && record.getCheckInAt().isBefore(overlapEnd) && record.getCheckOutAt().isAfter(overlapStart)) {
                throw new ConflictException(ErrorCode.CONFLICT,
                    "Leave overlaps worked time on " + day.workDate());
            }
            Instant now = Instant.now();
            if (record == null) {
                record = AttendanceRecord.builder()
                    .employee(request.getEmployee())
                    .workDate(day.workDate())
                    .shift(day.shift())
                    .scheduledStartAt(day.startAt())
                    .scheduledEndAt(day.endAt())
                    .scheduledMinutes(day.minutes())
                    .workedMinutes(0)
                    .payableMinutes(0)
                    .lateMinutes(0)
                    .earlyLeaveMinutes(0)
                    .overtimeMinutes(0)
                    .overtimeMultiplier(java.math.BigDecimal.ONE)
                    .status(AttendanceStatus.MISSING_PUNCH)
                    .createdAt(now)
                    .build();
            }
            record.setLeaveRequest(request);
            record.setLeaveMinutes(leaveMinutes);
            if (record.getCheckInAt() != null) {
                // The other part of the day was worked: late, early and payable now exclude the leave.
                applyPunches(record);
            } else if (request.getSalaryTreatment() == LeaveSalaryTreatment.EMPLOYER_PAID) {
                record.setPayableMinutes(Math.min(record.getScheduledMinutes(), record.getPayableMinutes() + leaveMinutes));
            }
            if (record.getCheckInAt() == null) {
                record.setStatus(leaveMinutes == record.getScheduledMinutes()
                    ? leaveStatus(request) : AttendanceStatus.MISSING_PUNCH);
            }
            record.setUpdatedAt(now);
            attendanceRecordRepository.save(record);
            remainingMinutes -= leaveMinutes;
        }
        if (remainingMinutes > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Requested leave exceeds scheduled work time by " + remainingMinutes + " minutes");
        }
    }

    private AttendanceStatus leaveStatus(LeaveRequest request) {
        if (request.getSalaryTreatment() == LeaveSalaryTreatment.EMPLOYER_PAID) {
            return AttendanceStatus.PAID_LEAVE;
        }
        if (request.getSalaryTreatment() == LeaveSalaryTreatment.SOCIAL_INSURANCE) {
            if (request.getLeaveType() == LeaveType.MATERNITY) {
                return AttendanceStatus.MATERNITY_LEAVE;
            }
            if (request.getLeaveType() == LeaveType.SICK) {
                return AttendanceStatus.SICK_LEAVE;
            }
        }
        return AttendanceStatus.UNPAID_LEAVE;
    }

    /**
     * Derives late, worked, payable and early-leave minutes from the punches. The shift break and
     * an approved leave on the same day are not counted as missing time, so half a day of leave
     * plus the other half worked adds up to the full scheduled day.
     */
    private void applyPunches(AttendanceRecord record) {
        WorkShift shift = record.getShift();
        Instant start = record.getScheduledStartAt();
        Instant end = record.getScheduledEndAt();
        Instant in = record.getCheckInAt();
        LeaveRequest leave = record.getLeaveRequest();
        record.setLateMinutes(Math.max(0,
            uncoveredWorkMinutes(start, end, shift, leave, start, in) - shift.getGraceLateMinutes()));
        Instant out = record.getCheckOutAt();
        if (out == null) {
            return;
        }
        int paidLeaveMinutes = leave != null && leave.getSalaryTreatment() == LeaveSalaryTreatment.EMPLOYER_PAID
            ? record.getLeaveMinutes() : 0;
        int payableMinutes = Math.min(record.getScheduledMinutes(),
            workMinutes(start, end, shift, in, out) + paidLeaveMinutes);
        if (attendanceCalendarService.companyWorkDates(record.getWorkDate(), record.getWorkDate()).isEmpty()) {
            payableMinutes = 0;
        }
        record.setWorkedMinutes((int) Math.max(0,
            Duration.between(in, out).toMinutes() - breakMinutesWithin(start, shift, in, out)));
        record.setPayableMinutes(payableMinutes);
        record.setEarlyLeaveMinutes(uncoveredWorkMinutes(start, end, shift, leave, out, end));
    }

    /** Scheduled work minutes in [from, to) covered by neither the shift break nor the leave. */
    private static int uncoveredWorkMinutes(Instant scheduledStart, Instant scheduledEnd, WorkShift shift,
        LeaveRequest leave, Instant from, Instant to) {
        int minutes = workMinutes(scheduledStart, scheduledEnd, shift, from, to);
        if (leave != null) {
            Instant leaveFrom = leave.getStartAt().isAfter(from) ? leave.getStartAt() : from;
            Instant leaveTo = leave.getEndAt().isBefore(to) ? leave.getEndAt() : to;
            minutes -= workMinutes(scheduledStart, scheduledEnd, shift, leaveFrom, leaveTo);
        }
        return Math.max(0, minutes);
    }

    /** Minutes of [from, to) inside the scheduled window, minus the part that falls in the shift break. */
    private static int workMinutes(Instant scheduledStart, Instant scheduledEnd, WorkShift shift,
        Instant from, Instant to) {
        return Math.max(0, overlapMinutes(from, to, scheduledStart, scheduledEnd)
            - breakMinutesWithin(scheduledStart, shift, from, to));
    }

    private static int breakMinutesWithin(Instant scheduledStart, WorkShift shift, Instant from, Instant to) {
        if (shift.getBreakStartTime() == null) {
            return 0;
        }
        long offset = Math.floorMod(
            Duration.between(shift.getStartTime(), shift.getBreakStartTime()).toMinutes(), 24L * 60L);
        Instant breakStart = scheduledStart.plus(Duration.ofMinutes(offset));
        return overlapMinutes(from, to, breakStart, breakStart.plus(Duration.ofMinutes(shift.getBreakMinutes())));
    }

    private static int overlapMinutes(Instant from, Instant to, Instant windowStart, Instant windowEnd) {
        Instant start = from.isAfter(windowStart) ? from : windowStart;
        Instant end = to.isBefore(windowEnd) ? to : windowEnd;
        return (int) Math.max(0, Duration.between(start, end).toMinutes());
    }

    @PreAuthorize("hasAuthority('attendance.overtime.approve')")
    public AttendanceRecordResponse approveOvertime(Long attendanceId, ApproveOvertimeRequest request) {
        AttendanceRecord record = findRecordForUpdateOrThrow(attendanceId);
        employeeAccessScopeService.requireEmployeeAccess(record.getEmployee().getId(), "attendance.overtime.approve");
        boolean scheduledDay = !attendanceCalendarService.scheduleFor(record.getEmployee().getId(),
            record.getWorkDate(), record.getWorkDate()).isEmpty();
        int paidLeaveMinutes = record.getLeaveRequest() != null
            && record.getLeaveRequest().getSalaryTreatment() == LeaveSalaryTreatment.EMPLOYER_PAID
                ? record.getLeaveMinutes() : 0;
        int regularWorkedMinutes = scheduledDay
            ? Math.min(record.getWorkedMinutes(), Math.max(0, record.getPayableMinutes() - paidLeaveMinutes)) : 0;
        int availableMinutes = Math.max(0, record.getWorkedMinutes() - regularWorkedMinutes);
        if (record.getCheckOutAt() == null || request.overtimeMinutes() > availableMinutes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Overtime requires completed attendance and cannot include regular payable time");
        }
        BigDecimal minimumMultiplier = attendanceCalendarService.isCompanyHoliday(record.getWorkDate())
            ? BigDecimal.valueOf(3)
            : record.getWorkDate().getDayOfWeek() == DayOfWeek.SUNDAY
                ? BigDecimal.valueOf(2) : BigDecimal.valueOf(1.5);
        if (request.overtimeMinutes() > 0 && request.overtimeMultiplier().compareTo(minimumMultiplier) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Overtime multiplier must be at least " + minimumMultiplier + " on " + record.getWorkDate(),
                "overtimeMultiplier");
        }
        Account approver = request.overtimeMinutes() > 0 ? findCurrentAccount() : null;

        Instant now = Instant.now();
        record.setOvertimeMinutes(request.overtimeMinutes());
        record.setOvertimeMultiplier(request.overtimeMultiplier());
        record.setOvertimeTaxExempt(request.overtimeMinutes() > 0 && Boolean.TRUE.equals(request.taxExempt()));
        record.setOvertimeApprovedByAccount(approver);
        record.setOvertimeApprovedAt(approver != null ? now : null);
        record.setUpdatedAt(now);

        return toResponse(record);
    }

    @PreAuthorize("hasAuthority('attendance.manage')")
    public AttendanceRecordResponse adjust(Long attendanceId, AdjustAttendanceRequest request) {
        AttendanceRecord record = findRecordForUpdateOrThrow(attendanceId);
        employeeAccessScopeService.requireEmployeeAccess(record.getEmployee().getId(), "attendance.manage");
        if (request.payableMinutes() > record.getScheduledMinutes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Payable time exceeds scheduled time", "payableMinutes");
        }
        if (request.status() == AttendanceStatus.MISSING_PUNCH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Adjustment must resolve MISSING_PUNCH", "status");
        }
        if (request.status() == AttendanceStatus.PRESENT && request.workedMinutes() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "PRESENT requires worked minutes", "workedMinutes");
        }
        if (request.status() == AttendanceStatus.UNAUTHORIZED_ABSENCE
            && (request.workedMinutes() != 0 || request.payableMinutes() != 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "UNAUTHORIZED_ABSENCE requires zero worked and payable minutes", "status");
        }
        Account updatedBy = findCurrentAccount();

        record.setWorkedMinutes(request.workedMinutes());
        record.setPayableMinutes(request.payableMinutes());
        record.setLateMinutes(request.lateMinutes());
        record.setEarlyLeaveMinutes(request.earlyLeaveMinutes());
        record.setStatus(request.status());
        record.setNote(request.note());
        record.setUpdatedByAccount(updatedBy);
        record.setUpdatedAt(Instant.now());

        return toResponse(record);
    }

    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    @Transactional(readOnly = true)
    public AttendanceRecordResponse getById(Long id) {
        AttendanceRecord record = findRecordOrThrow(id);
        requireSelfOrScopedAccess(record.getEmployee().getId(), "attendance.self.read", "attendance.read");
        return toResponse(record);
    }

    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    @Transactional(readOnly = true)
    public Page<AttendanceRecordResponse> listByEmployee(Long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        requireSelfOrScopedAccess(employeeId, "attendance.self.read", "attendance.read");
        return attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(employeeId, from, to, pageable)
            .map(this::toResponse);
    }

    private AttendanceRecord findRecordOrThrow(Long id) {
        return attendanceRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + id));
    }

    private AttendanceRecord findRecordForUpdateOrThrow(Long id) {
        AttendanceRecord existing = findRecordOrThrow(id);
        assertAttendanceEditable(existing.getWorkDate());
        return attendanceRecordRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + id));
    }

    private void assertAttendanceEditable(LocalDate workDate) {
        payrollPeriodRepository.findContainingDateForUpdate(workDate)
            .filter(period -> period.getStatus() == PayrollPeriodStatus.APPROVED
                || period.getStatus() == PayrollPeriodStatus.PAID
                || period.getStatus() == PayrollPeriodStatus.LOCKED)
            .ifPresent(period -> {
                throw new ConflictException(ErrorCode.PAYROLL_PERIOD_LOCKED,
                    "Attendance belongs to approved payroll period " + period.getId());
            });
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + accountId));
    }

    private void requireSelfOrScopedAccess(Long employeeId, String selfPermission, String scopedPermission) {
        if (currentAccountProvider.hasAuthority(selfPermission)) {
            Account actor = findCurrentAccount();
            if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
                return;
            }
        }
        if (!currentAccountProvider.hasAuthority(scopedPermission)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Employee is outside the assigned scope");
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, scopedPermission);
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record) {
        return new AttendanceRecordResponse(
            record.getId(),
            record.getEmployee().getId(),
            record.getWorkDate(),
            record.getShift().getId(),
            record.getScheduledStartAt(),
            record.getScheduledEndAt(),
            record.getScheduledMinutes(),
            record.getCheckInAt(),
            record.getCheckOutAt(),
            record.getWorkedMinutes(),
            record.getPayableMinutes(),
            record.getLateMinutes(),
            record.getEarlyLeaveMinutes(),
            record.getOvertimeMinutes(),
            record.getOvertimeMultiplier(),
            record.isOvertimeTaxExempt(),
            record.getStatus()
        );
    }
}
