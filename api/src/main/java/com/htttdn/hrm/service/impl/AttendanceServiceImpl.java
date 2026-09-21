package com.htttdn.hrm.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.service.AttendanceService;

@Service
@Transactional
public class AttendanceServiceImpl implements AttendanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AccountRepository accountRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;

    public AttendanceServiceImpl(
        AttendanceRecordRepository attendanceRecordRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AccountRepository accountRepository,
        PayrollPeriodRepository payrollPeriodRepository
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.accountRepository = accountRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
    }

    @Override
    public AttendanceRecordResponse checkIn(CheckInRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
            .filter(e -> e.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + request.employeeId()));

        LocalDate workDate = LocalDate.now(ZONE);
        if (attendanceRecordRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate).isPresent()) {
            throw new ConflictException(ErrorCode.CONFLICT, "Employee has already checked in today");
        }

        EmployeeAssignment assignment = employeeAssignmentRepository
            .findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employee.getId())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.VALIDATION_ERROR, "Employee has no active assignment", "employeeId"));

        WorkShift shift = assignment.getShift();
        if (shift == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Employee has no shift assigned", "employeeId");
        }

        Instant scheduledStartAt = toInstant(workDate, shift.getStartTime());
        Instant scheduledEndAt = toInstant(shift.getCrossesMidnight() ? workDate.plusDays(1) : workDate, shift.getEndTime());
        Instant now = Instant.now();

        long lateMinutes = Math.max(0, Duration.between(scheduledStartAt, now).toMinutes() - shift.getGraceLateMinutes());

        AttendanceRecord record = AttendanceRecord.builder()
            .employee(employee)
            .workDate(workDate)
            .shift(shift)
            .scheduledStartAt(scheduledStartAt)
            .scheduledEndAt(scheduledEndAt)
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
    public AttendanceRecordResponse checkOut(CheckOutRequest request) {
        AttendanceRecord record = attendanceRecordRepository
            .findByEmployeeIdAndWorkDate(request.employeeId(), request.workDate())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ATTENDANCE_NOT_FOUND,
                "No attendance record for employee " + request.employeeId() + " on " + request.workDate()));

        if (record.getCheckInAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Employee has not checked in yet");
        }

        Instant now = Instant.now();
        record.setCheckOutAt(now);

        WorkShift shift = record.getShift();
        long workedMinutes = Duration.between(record.getCheckInAt(), now).toMinutes();
        long payableMinutes = Math.min(workedMinutes, shift.getStandardWorkMinutes());
        long earlyLeaveMinutes = Math.max(0, Duration.between(now, record.getScheduledEndAt()).toMinutes());

        record.setWorkedMinutes((int) workedMinutes);
        record.setPayableMinutes((int) payableMinutes);
        record.setEarlyLeaveMinutes((int) earlyLeaveMinutes);
        record.setUpdatedAt(now);

        return toResponse(record);
    }

    @Override
    public AttendanceRecordResponse approveOvertime(Long attendanceId, ApproveOvertimeRequest request) {
        AttendanceRecord record = findRecordOrThrow(attendanceId);
        Account approver = accountRepository.findById(request.approverAccountId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.approverAccountId()));

        Instant now = Instant.now();
        record.setOvertimeMinutes(request.overtimeMinutes());
        record.setOvertimeMultiplier(request.overtimeMultiplier());
        record.setOvertimeApprovedByAccount(approver);
        record.setOvertimeApprovedAt(now);
        record.setUpdatedAt(now);

        return toResponse(record);
    }

    @Override
    public AttendanceRecordResponse adjust(Long attendanceId, AdjustAttendanceRequest request) {
        AttendanceRecord record = findRecordOrThrow(attendanceId);

        payrollPeriodRepository
            .findFirstByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(record.getWorkDate(), record.getWorkDate())
            .filter(period -> period.getStatus() == PayrollPeriodStatus.APPROVED
                || period.getStatus() == PayrollPeriodStatus.PAID
                || period.getStatus() == PayrollPeriodStatus.LOCKED)
            .ifPresent(period -> {
                throw new ConflictException(
                    ErrorCode.PAYROLL_PERIOD_LOCKED,
                    "Cannot adjust attendance already used in payroll period " + period.getId());
            });

        Account updatedBy = accountRepository.findById(request.updatedByAccountId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + request.updatedByAccountId()));

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
    @Transactional(readOnly = true)
    public AttendanceRecordResponse getById(Long id) {
        return toResponse(findRecordOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AttendanceRecordResponse> listByEmployee(Long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        return attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(employeeId, from, to, pageable)
            .map(this::toResponse);
    }

    private AttendanceRecord findRecordOrThrow(Long id) {
        return attendanceRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + id));
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
