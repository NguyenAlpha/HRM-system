package com.htttdn.hrm.service.attendance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
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
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Ghi công: tạo sẵn ngày công của tháng, ghi giờ vào/ra, áp đơn nghỉ đã duyệt và HR chỉnh công. Tăng ca
 * nằm ở {@link OvertimeService}, phần chỉ đọc ở {@link AttendanceQueryService}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AttendanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Set by an approved leave covering the whole day; changed only through the leave workflow. */
    private static final Set<AttendanceStatus> FULL_DAY_LEAVE = Set.of(AttendanceStatus.PAID_LEAVE,
        AttendanceStatus.UNPAID_LEAVE, AttendanceStatus.MATERNITY_LEAVE, AttendanceStatus.SICK_LEAVE);

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceCalendarService attendanceCalendarService;
    private final AttendanceEditGuard attendanceEditGuard;
    private final AttendanceMapper attendanceMapper;

    @PreAuthorize("hasAuthority('attendance.manage')")
    public int prepareMonth(int year, int month) {
        employeeAccessScopeService.requireCompanyWide("attendance.manage");
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        attendanceEditGuard.requireEditable(from);
        int created = 0;
        for (Employee employee : employeeRepository.findEmployedDuring(from, to)) {
            created += prepareEmployee(employee, from, to);
        }
        return created;
    }

    /**
     * {@link #prepareMonth} for one employee, used by the background job: there is no caller to
     * authorize, and each employee runs in its own transaction so one with a missing assignment
     * or shift does not roll back the others.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int autoPrepareEmployee(Long employeeId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        attendanceEditGuard.requireEditable(from);
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + employeeId));
        return prepareEmployee(employee, from, to);
    }

    /** Checks the whole schedule before saving, so a failing employee gets no records at all. */
    private int prepareEmployee(Employee employee, LocalDate from, LocalDate to) {
        int created = 0;
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
            attendanceRecordRepository.save(newRecord(employee, day));
            created++;
        }
        return created;
    }

    /**
     * Records one day's check-in and check-out with explicit timestamps, the way a time clock
     * reports them, and derives late, early-leave, worked and payable minutes from them. HR also
     * uses it to reconcile a day: it overwrites earlier punches or an earlier adjustment, and
     * creates the day from the assignment's shift when it has no record yet (Sundays, holidays).
     * Approved overtime is cleared because it was approved against the old punches.
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
        attendanceEditGuard.requireEditable(workDate);

        AttendanceRecord record = attendanceRecordRepository
            .findByEmployeeIdAndWorkDateForUpdate(employee.getId(), workDate).orElse(null);
        if (record != null && FULL_DAY_LEAVE.contains(record.getStatus())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Attendance on " + workDate + " is a full day of approved leave");
        }
        if (record != null && record.getLeaveRequest() != null
            && in.isBefore(record.getLeaveRequest().getEndAt()) && out.isAfter(record.getLeaveRequest().getStartAt())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Worked time overlaps approved leave");
        }
        if (record == null) {
            AttendanceCalendarService.ScheduledDay day = attendanceCalendarService
                .assignedDay(employee.getId(), workDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Employee has no primary assignment on " + workDate, "workDate"));
            record = newRecord(employee, day);
        }
        record.setCheckInAt(in);
        record.setCheckOutAt(out);
        applyPunches(record);
        record.setStatus(AttendanceStatus.PRESENT);
        record.setOvertimeMinutes(0);
        record.setOvertimeMultiplier(java.math.BigDecimal.ONE);
        record.setOvertimeTaxExempt(false);
        record.setOvertimeApprovedByAccount(null);
        record.setOvertimeApprovedAt(null);
        record.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        record.setUpdatedByAccount(currentAccountProvider.account());
        return attendanceMapper.toResponse(attendanceRecordRepository.save(record));
    }

    /**
     * The scheduled work minutes a leave from {@code startAt} to {@code endAt} takes: the shift
     * time it covers on each work day, without breaks, Sundays and company holidays. The day
     * before {@code startAt} is read too, for a night shift that ends inside the leave.
     */
    @Transactional(readOnly = true)
    public int leaveMinutesFor(Long employeeId, Instant startAt, Instant endAt) {
        LocalDate from = LocalDate.ofInstant(startAt, ZONE).minusDays(1);
        LocalDate to = LocalDate.ofInstant(endAt.minusNanos(1), ZONE);
        int minutes = 0;
        for (AttendanceCalendarService.ScheduledDay day : attendanceCalendarService.scheduleFor(employeeId, from, to)) {
            minutes += Math.min(day.minutes(), workMinutes(day.startAt(), day.endAt(), day.shift(), startAt, endAt));
        }
        return minutes;
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
            attendanceEditGuard.requireEditable(day.workDate());
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
                record = newRecord(request.getEmployee(), day);
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

    @PreAuthorize("hasAuthority('attendance.manage')")
    public AttendanceRecordResponse adjust(Long attendanceId, AdjustAttendanceRequest request) {
        AttendanceRecord record = attendanceEditGuard.getEditableForUpdate(attendanceId);
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
        Account updatedBy = currentAccountProvider.account();

        record.setWorkedMinutes(request.workedMinutes());
        record.setPayableMinutes(request.payableMinutes());
        record.setLateMinutes(request.lateMinutes());
        record.setEarlyLeaveMinutes(request.earlyLeaveMinutes());
        record.setStatus(request.status());
        record.setNote(request.note());
        record.setUpdatedByAccount(updatedBy);

        return attendanceMapper.toResponse(record);
    }

    /** Ngày công trống theo lịch làm: chưa chấm giờ, chưa có phút nào, trạng thái thiếu chấm công. */
    private static AttendanceRecord newRecord(Employee employee, AttendanceCalendarService.ScheduledDay day) {
        return AttendanceRecord.builder()
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
            .overtimeMultiplier(BigDecimal.ONE)
            .status(AttendanceStatus.MISSING_PUNCH)
            .build();
    }
}
