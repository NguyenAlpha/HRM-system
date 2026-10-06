package com.htttdn.hrm.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import com.htttdn.hrm.dto.request.attendance.CheckInRequest;
import com.htttdn.hrm.dto.request.attendance.CheckOutRequest;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
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
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.AttendanceService;
import com.htttdn.hrm.service.AttendanceCalendarService;
import com.htttdn.hrm.service.EmployeeAccessScopeService;

@Service
@Transactional
public class AttendanceServiceImpl implements AttendanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AccountRepository accountRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceCalendarService attendanceCalendarService;

    public AttendanceServiceImpl(
        AttendanceRecordRepository attendanceRecordRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AccountRepository accountRepository,
        PayrollPeriodRepository payrollPeriodRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService,
        AttendanceCalendarService attendanceCalendarService
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.accountRepository = accountRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.attendanceCalendarService = attendanceCalendarService;
    }

    @Override
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

    @Override
    public void applyApprovedLeave(LeaveRequest request) {
        Long employeeId = request.getEmployee().getId();
        LocalDate from = LocalDate.ofInstant(request.getStartAt(), ZONE).minusDays(1);
        LocalDate to = LocalDate.ofInstant(request.getEndAt().minusNanos(1), ZONE);
        int remainingMinutes = request.getRequestedMinutes();
        for (AttendanceCalendarService.ScheduledDay day : attendanceCalendarService.scheduleFor(employeeId, from, to)) {
            Instant overlapStart = request.getStartAt().isAfter(day.startAt()) ? request.getStartAt() : day.startAt();
            Instant overlapEnd = request.getEndAt().isBefore(day.endAt()) ? request.getEndAt() : day.endAt();
            int overlapMinutes = (int) Math.max(0, Duration.between(overlapStart, overlapEnd).toMinutes());
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
            if (request.getSalaryTreatment() == LeaveSalaryTreatment.EMPLOYER_PAID) {
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

    @Override
    @PreAuthorize("hasAnyAuthority('attendance.self.record', 'attendance.manage')")
    public AttendanceRecordResponse checkIn(CheckInRequest request) {
        requireSelfOrScopedAccess(request.employeeId(), "attendance.self.record", "attendance.manage");
        Employee employee = employeeRepository.findById(request.employeeId())
            .filter(e -> e.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + request.employeeId()));

        Instant now = Instant.now();
        LocalDate workDate = LocalDate.ofInstant(now, ZONE);
        LocalDate previousDate = workDate.minusDays(1);
        EmployeeAssignment previousAssignment = employeeAssignmentRepository
            .findCurrentPrimaryCandidates(employee.getId(), previousDate).stream().findFirst().orElse(null);
        if (previousAssignment != null && previousAssignment.getShift() != null
            && Boolean.TRUE.equals(previousAssignment.getShift().getCrossesMidnight())
            && now.isBefore(toInstant(workDate, previousAssignment.getShift().getEndTime()))) {
            workDate = previousDate;
        }
        assertAttendanceEditable(workDate);
        AttendanceRecord existing = attendanceRecordRepository
            .findByEmployeeIdAndWorkDateForUpdate(employee.getId(), workDate).orElse(null);
        if (existing != null && (existing.getCheckInAt() != null || existing.getCheckOutAt() != null
            || existing.getStatus() != AttendanceStatus.MISSING_PUNCH)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Attendance record cannot be checked in again");
        }
        if (existing != null && existing.getLeaveRequest() != null
            && !now.isBefore(existing.getLeaveRequest().getStartAt())
            && now.isBefore(existing.getLeaveRequest().getEndAt())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Check-in overlaps approved leave");
        }
        EmployeeAssignment assignment = existing == null ? employeeAssignmentRepository
            .findCurrentPrimaryCandidates(employee.getId(), workDate).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                ErrorCode.VALIDATION_ERROR, "Employee has no active assignment", "employeeId")) : null;

        WorkShift shift = existing != null ? existing.getShift() : assignment.getShift();
        if (shift == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Employee has no shift assigned", "employeeId");
        }

        Instant scheduledStartAt = existing != null ? existing.getScheduledStartAt()
            : toInstant(workDate, shift.getStartTime());
        Instant scheduledEndAt = existing != null ? existing.getScheduledEndAt()
            : toInstant(shift.getCrossesMidnight() ? workDate.plusDays(1) : workDate, shift.getEndTime());
        long lateMinutes = Math.max(0, Duration.between(scheduledStartAt, now).toMinutes() - shift.getGraceLateMinutes());

        if (existing != null) {
            existing.setCheckInAt(now);
            existing.setLateMinutes((int) lateMinutes);
            existing.setStatus(AttendanceStatus.PRESENT);
            existing.setUpdatedAt(now);
            return toResponse(existing);
        }

        AttendanceRecord record = AttendanceRecord.builder()
            .employee(employee)
            .workDate(workDate)
            .shift(shift)
            .scheduledStartAt(scheduledStartAt)
            .scheduledEndAt(scheduledEndAt)
            .scheduledMinutes(shift.getStandardWorkMinutes())
            .checkInAt(now)
            .workedMinutes(0)
            .payableMinutes(0)
            .lateMinutes((int) lateMinutes)
            .earlyLeaveMinutes(0)
            .overtimeMinutes(0)
            .overtimeMultiplier(java.math.BigDecimal.ONE)
            .status(AttendanceStatus.PRESENT)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return toResponse(attendanceRecordRepository.save(record));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('attendance.self.record', 'attendance.manage')")
    public AttendanceRecordResponse checkOut(CheckOutRequest request) {
        requireSelfOrScopedAccess(request.employeeId(), "attendance.self.record", "attendance.manage");
        assertAttendanceEditable(request.workDate());
        AttendanceRecord record = attendanceRecordRepository
            .findByEmployeeIdAndWorkDateForUpdate(request.employeeId(), request.workDate())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ATTENDANCE_NOT_FOUND,
                "No attendance record for employee " + request.employeeId() + " on " + request.workDate()));

        if (record.getCheckInAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Employee has not checked in yet");
        }
        if (record.getCheckOutAt() != null) {
            throw new ConflictException(ErrorCode.CONFLICT, "Employee has already checked out");
        }

        Instant now = Instant.now();
        if (!now.isAfter(record.getCheckInAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Check-out must be after check-in");
        }
        if (record.getLeaveRequest() != null
            && record.getCheckInAt().isBefore(record.getLeaveRequest().getEndAt())
            && now.isAfter(record.getLeaveRequest().getStartAt())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Worked time overlaps approved leave");
        }

        WorkShift shift = record.getShift();
        long workedMinutes = Math.max(0,
            Duration.between(record.getCheckInAt(), now).toMinutes() - shift.getBreakMinutes());
        Instant payableStart = record.getCheckInAt().isAfter(record.getScheduledStartAt())
            ? record.getCheckInAt() : record.getScheduledStartAt();
        Instant payableEnd = now.isBefore(record.getScheduledEndAt()) ? now : record.getScheduledEndAt();
        long scheduledOverlap = Math.max(0, Duration.between(payableStart, payableEnd).toMinutes());
        long payableMinutes = Math.min(record.getScheduledMinutes(),
            Math.max(0, scheduledOverlap - shift.getBreakMinutes())
                + (record.getLeaveRequest() != null
                    && record.getLeaveRequest().getSalaryTreatment() == LeaveSalaryTreatment.EMPLOYER_PAID
                    ? record.getLeaveMinutes() : 0));
        long earlyLeaveMinutes = Math.max(0, Duration.between(now, record.getScheduledEndAt()).toMinutes());

        record.setCheckOutAt(now);
        record.setWorkedMinutes((int) workedMinutes);
        record.setPayableMinutes((int) payableMinutes);
        record.setEarlyLeaveMinutes((int) earlyLeaveMinutes);
        record.setUpdatedAt(now);

        return toResponse(record);
    }

    @Override
    @PreAuthorize("hasAuthority('attendance.overtime.approve')")
    public AttendanceRecordResponse approveOvertime(Long attendanceId, ApproveOvertimeRequest request) {
        AttendanceRecord record = findRecordForUpdateOrThrow(attendanceId);
        employeeAccessScopeService.requireEmployeeAccess(record.getEmployee().getId(), "attendance.overtime.approve");
        boolean scheduledDay = !attendanceCalendarService.scheduleFor(record.getEmployee().getId(),
            record.getWorkDate(), record.getWorkDate()).isEmpty();
        int availableMinutes = scheduledDay
            ? Math.max(0, record.getWorkedMinutes() - record.getPayableMinutes())
            : record.getWorkedMinutes();
        if (record.getCheckOutAt() == null || request.overtimeMinutes() > availableMinutes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Overtime requires completed attendance and cannot include regular payable time");
        }
        Account approver = request.overtimeMinutes() > 0 ? findCurrentAccount() : null;

        Instant now = Instant.now();
        record.setOvertimeMinutes(request.overtimeMinutes());
        record.setOvertimeMultiplier(request.overtimeMultiplier());
        record.setOvertimeApprovedByAccount(approver);
        record.setOvertimeApprovedAt(approver != null ? now : null);
        record.setUpdatedAt(now);

        return toResponse(record);
    }

    @Override
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

    @Override
    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    @Transactional(readOnly = true)
    public AttendanceRecordResponse getById(Long id) {
        AttendanceRecord record = findRecordOrThrow(id);
        requireSelfOrScopedAccess(record.getEmployee().getId(), "attendance.self.read", "attendance.read");
        return toResponse(record);
    }

    @Override
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

    private Instant toInstant(LocalDate date, java.time.LocalTime time) {
        return LocalDateTime.of(date, time).atZone(ZONE).toInstant();
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord record) {
        return new AttendanceRecordResponse(
            record.getId(),
            record.getEmployee().getId(),
            record.getWorkDate(),
            record.getShift().getId(),
            record.getScheduledStartAt(),
            record.getScheduledEndAt(),
            record.getCheckInAt(),
            record.getCheckOutAt(),
            record.getWorkedMinutes(),
            record.getPayableMinutes(),
            record.getLateMinutes(),
            record.getEarlyLeaveMinutes(),
            record.getOvertimeMinutes(),
            record.getOvertimeMultiplier(),
            record.getStatus()
        );
    }
}
