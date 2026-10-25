package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.request.attendance.RecordPunchesRequest;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.attendance.OvertimeApprovalItemResponse;
import com.htttdn.hrm.dto.response.attendance.TimesheetRowResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

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
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceCalendarService attendanceCalendarService;
    private final ApproverResolver approverResolver;

    @PreAuthorize("hasAuthority('attendance.manage')")
    public int prepareMonth(int year, int month) {
        employeeAccessScopeService.requireCompanyWide("attendance.manage");
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        assertAttendanceEditable(from);
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
        assertAttendanceEditable(from);
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
                .build());
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
        assertAttendanceEditable(workDate);

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
                .build();
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
        return toResponse(attendanceRecordRepository.save(record));
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

    /**
     * The approver of the employee (direct manager, else Director, else Company Owner) decides the
     * overtime of a day; zero minutes records that the extra time is not paid. Only the Company Owner,
     * with nobody above, confirms their own overtime.
     */
    @PreAuthorize("isAuthenticated()")
    public AttendanceRecordResponse approveOvertime(Long attendanceId, ApproveOvertimeRequest request) {
        AttendanceRecord record = findRecordForUpdateOrThrow(attendanceId);
        requireOvertimeApprover(record.getEmployee());
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
        // The decision is recorded even for zero minutes, so the day leaves the approver's queue.
        Instant now = Instant.now();
        record.setOvertimeMinutes(request.overtimeMinutes());
        record.setOvertimeMultiplier(request.overtimeMultiplier());
        record.setOvertimeTaxExempt(request.overtimeMinutes() > 0 && Boolean.TRUE.equals(request.taxExempt()));
        record.setOvertimeApprovedByAccount(currentAccountProvider.account());
        record.setOvertimeApprovedAt(now);

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
        Account updatedBy = currentAccountProvider.account();

        record.setWorkedMinutes(request.workedMinutes());
        record.setPayableMinutes(request.payableMinutes());
        record.setLateMinutes(request.lateMinutes());
        record.setEarlyLeaveMinutes(request.earlyLeaveMinutes());
        record.setStatus(request.status());
        record.setNote(request.note());
        record.setUpdatedByAccount(updatedBy);

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

    /**
     * Bảng công tháng của mọi nhân viên trong scope {@code attendance.read} có làm việc trong tháng,
     * xếp theo mã nhân viên. Lọc thêm theo từ khóa (mã, tên, email) và đơn vị của phân công hiện tại.
     */
    @PreAuthorize("hasAuthority('attendance.read')")
    @Transactional(readOnly = true)
    public List<TimesheetRowResponse> timesheet(int year, int month, String keyword, Long organizationUnitId) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        Specification<Employee> employed = (root, query, builder) -> builder.and(
            builder.lessThanOrEqualTo(root.get("hireDate"), to),
            builder.or(builder.isNull(root.get("terminationDate")), builder.greaterThanOrEqualTo(root.get("terminationDate"), from))
        );
        Specification<Employee> specification = employeeAccessScopeService.accessibleEmployees("attendance.read").and(employed);
        for (Specification<Employee> filter : Arrays.asList(
            EmployeeSpecifications.matchesKeyword(keyword),
            EmployeeSpecifications.currentlyAssignedTo(organizationUnitId, null, LocalDate.now(ZONE))
        )) {
            if (filter != null) {
                specification = specification.and(filter);
            }
        }
        List<Employee> employees = employeeRepository.findAll(specification, Sort.by("employeeCode"));
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttendanceRecordResponse>> records = attendanceRecordRepository
            .findByEmployeeIdInAndWorkDateBetweenOrderByWorkDateAsc(employees.stream().map(Employee::getId).toList(), from, to)
            .stream()
            .collect(Collectors.groupingBy(record -> record.getEmployee().getId(),
                Collectors.mapping(this::toResponse, Collectors.toList())));
        return employees.stream()
            .map(employee -> new TimesheetRowResponse(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                records.getOrDefault(employee.getId(), List.of())))
            .toList();
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

    private void requireSelfOrScopedAccess(Long employeeId, String selfPermission, String scopedPermission) {
        if (currentAccountProvider.hasAuthority(selfPermission)) {
            Account actor = currentAccountProvider.account();
            if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
                return;
            }
        }
        if (!currentAccountProvider.hasAuthority(scopedPermission)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Employee is outside the assigned scope");
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, scopedPermission);
    }

    /** The days with extra time, of the employees whose overtime the signed-in employee decides. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<OvertimeApprovalItemResponse> listOvertimeAwaitingDecision() {
        Employee self = currentAccountProvider.account().getEmployee();
        if (self == null) {
            return List.of();
        }
        List<Employee> employed = employeeRepository.findByEmploymentStatusInAndDeletedAtIsNull(
            List.of(EmploymentStatus.PROBATION, EmploymentStatus.ACTIVE));
        List<Long> employeeIds = new ArrayList<>(approverResolver.decidedBy(self, employed));
        if (approverResolver.approverFor(self).isEmpty() && approverResolver.isTopManagement(self)) {
            employeeIds.add(self.getId());
        }
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return attendanceRecordRepository.findOvertimeAwaitingDecision(employeeIds).stream()
            .map(record -> new OvertimeApprovalItemResponse(
                toResponse(record),
                record.getEmployee().getEmployeeCode(),
                record.getEmployee().getFullName()
            ))
            .toList();
    }

    private void requireOvertimeApprover(Employee employee) {
        Employee self = currentAccountProvider.account().getEmployee();
        if (self == null) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Only the employee's approver can decide overtime");
        }
        Optional<Employee> approver = approverResolver.approverFor(employee);
        boolean allowed = approver.isPresent()
            ? approver.get().getId().equals(self.getId())
            : self.getId().equals(employee.getId()) && approverResolver.isTopManagement(employee);
        if (!allowed) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Only the employee's approver can decide overtime");
        }
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
