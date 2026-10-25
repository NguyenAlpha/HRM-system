package com.htttdn.hrm.service.attendance;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.attendance.OvertimeApprovalItemResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.employee.ApproverResolver;

import lombok.RequiredArgsConstructor;

/** Duyệt tăng ca của một ngày công và danh sách ngày đang chờ người duyệt quyết định. */
@Service
@Transactional
@RequiredArgsConstructor
public class OvertimeService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final AttendanceCalendarService attendanceCalendarService;
    private final ApproverResolver approverResolver;
    private final AttendanceEditGuard attendanceEditGuard;
    private final AttendanceMapper attendanceMapper;

    /**
     * The approver of the employee (direct manager, else Director, else Company Owner) decides the
     * overtime of a day; zero minutes records that the extra time is not paid. Only the Company Owner,
     * with nobody above, confirms their own overtime.
     */
    @PreAuthorize("isAuthenticated()")
    public AttendanceRecordResponse approveOvertime(Long attendanceId, ApproveOvertimeRequest request) {
        AttendanceRecord record = attendanceEditGuard.getEditableForUpdate(attendanceId);
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

        return attendanceMapper.toResponse(record);
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
                attendanceMapper.toResponse(record),
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
}
