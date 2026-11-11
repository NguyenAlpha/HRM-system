package com.htttdn.hrm.service.attendance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.attendance.WebCheckInSettingResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.CompanyProfileRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;

import lombok.RequiredArgsConstructor;

/**
 * Chấm công trên web: khi công ty bật, nhân viên tự bấm Vào ca rồi Ra ca cho ngày hôm nay, theo giờ
 * của máy chủ (không tự khai giờ). Giờ vào/ra tính công y như giờ HR nhập
 * ({@link AttendanceService#recordPunches}); quên Ra ca thì ngày vẫn {@code MISSING_PUNCH} để HR sửa.
 *
 * <p>Tắt (mặc định) khi công ty dùng máy chấm công. Bật/tắt cần {@code attendance.web_check_in.manage}
 * phạm vi toàn công ty (Company Owner, System Admin).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class WebCheckInService {

    private static final short COMPANY_PROFILE_ID = 1;
    private static final Set<AttendanceStatus> FULL_DAY_LEAVE = Set.of(AttendanceStatus.PAID_LEAVE,
        AttendanceStatus.UNPAID_LEAVE, AttendanceStatus.MATERNITY_LEAVE, AttendanceStatus.SICK_LEAVE);
    private static final String WEB_NOTE = "Chấm công trên web";

    private final CompanyProfileRepository companyProfileRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceCalendarService attendanceCalendarService;
    private final AttendanceEditGuard attendanceEditGuard;
    private final AttendanceDayCalculator attendanceDayCalculator;
    private final AttendanceMapper attendanceMapper;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final Clock clock;

    /** Whether web check-in is on; read by the employee's check-in card and by whoever may switch it. */
    @PreAuthorize("hasAnyAuthority('attendance.self.check_in', 'attendance.web_check_in.manage')")
    @Transactional(readOnly = true)
    public WebCheckInSettingResponse getSetting() {
        return new WebCheckInSettingResponse(enabled());
    }

    /**
     * Bật/tắt chấm công trên web cho cả công ty.
     *
     * @throws com.htttdn.hrm.exception.ForbiddenException không có phạm vi COMPANY (→ 403)
     * @throws ResourceNotFoundException chưa có hồ sơ doanh nghiệp (→ 404)
     */
    @PreAuthorize("hasAuthority('attendance.web_check_in.manage')")
    public WebCheckInSettingResponse setEnabled(boolean enabled) {
        employeeAccessScopeService.requireCompanyWide("attendance.web_check_in.manage");
        CompanyProfile profile = companyProfileRepository.findById(COMPANY_PROFILE_ID)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Company profile not set up"));
        profile.setWebCheckInEnabled(enabled);
        return new WebCheckInSettingResponse(companyProfileRepository.saveAndFlush(profile).getWebCheckInEnabled());
    }

    /**
     * Vào ca: ghi giờ hiện tại làm giờ vào của ngày hôm nay; chỉ tính đi muộn, ngày chờ Ra ca.
     *
     * @throws ConflictException tính năng đang tắt (→ 409 {@code ATTENDANCE_WEB_CHECK_IN_DISABLED}), đã Vào ca
     *         (→ 409 {@code ATTENDANCE_ALREADY_CHECKED_IN}), ngày nghỉ trọn ca, đang trong khoảng nghỉ đã duyệt
     *         hoặc kỳ lương đã chốt (→ 409)
     * @throws BusinessException chưa vào làm/đã nghỉ việc, hoặc không có phân công chính hôm nay (→ 400)
     * @throws ResourceNotFoundException tài khoản không gắn nhân viên (→ 404)
     */
    @PreAuthorize("hasAuthority('attendance.self.check_in')")
    public AttendanceRecordResponse checkIn() {
        requireEnabled();
        Account account = currentAccountProvider.account();
        Employee employee = self(account);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ClockConfig.BUSINESS_ZONE);
        if (!employee.isEmployedDuring(today, today)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Employee is not employed on " + today, "workDate");
        }
        attendanceEditGuard.requireEditable(today);

        AttendanceRecord record = attendanceRecordRepository
            .findByEmployeeIdAndWorkDateForUpdate(employee.getId(), today).orElse(null);
        if (record == null) {
            AttendanceCalendarService.ScheduledDay day = attendanceCalendarService.assignedDay(employee.getId(), today)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_SCHEDULE_MISSING,
                    "Employee has no primary assignment on " + today, "workDate"));
            record = AttendanceDayCalculator.newRecord(employee, day);
        }
        requireNotFullDayLeave(record);
        if (record.getCheckInAt() != null) {
            throw new ConflictException(ErrorCode.ATTENDANCE_ALREADY_CHECKED_IN, "Already checked in on " + today);
        }
        LeaveRequest leave = record.getLeaveRequest();
        if (leave != null && !now.isBefore(leave.getStartAt()) && now.isBefore(leave.getEndAt())) {
            throw new ConflictException(ErrorCode.ATTENDANCE_OVERLAPS_LEAVE, "Checking in during approved leave");
        }
        record.setCheckInAt(now);
        attendanceDayCalculator.applyPunches(record);
        record.setStatus(AttendanceStatus.MISSING_PUNCH);
        if (record.getNote() == null) {
            record.setNote(WEB_NOTE);
        }
        record.setUpdatedByAccount(account);
        return attendanceMapper.toResponse(attendanceRecordRepository.save(record));
    }

    /**
     * Ra ca: ghi giờ hiện tại làm giờ ra của ngày đang Vào ca và tính công. Bấm lại thì giờ ra được cập nhật.
     * Ca qua đêm Ra ca vào sáng hôm sau, nên hôm qua còn dở (có giờ vào, chưa có giờ ra) cũng được nhận.
     *
     * @throws ConflictException tính năng đang tắt, chưa Vào ca (→ 409 {@code ATTENDANCE_NOT_CHECKED_IN}),
     *         giờ làm trùng khoảng nghỉ đã duyệt hoặc kỳ lương đã chốt (→ 409)
     * @throws ResourceNotFoundException tài khoản không gắn nhân viên (→ 404)
     */
    @PreAuthorize("hasAuthority('attendance.self.check_in')")
    public AttendanceRecordResponse checkOut() {
        requireEnabled();
        Account account = currentAccountProvider.account();
        Employee employee = self(account);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ClockConfig.BUSINESS_ZONE);

        AttendanceRecord record = checkedIn(employee.getId(), today)
            .or(() -> checkedIn(employee.getId(), today.minusDays(1))
                .filter(yesterday -> yesterday.getCheckOutAt() == null
                    && Boolean.TRUE.equals(yesterday.getShift().getCrossesMidnight())))
            .orElseThrow(() -> new ConflictException(ErrorCode.ATTENDANCE_NOT_CHECKED_IN,
                "Not checked in on " + today));
        attendanceEditGuard.requireEditable(record.getWorkDate());
        requireNotFullDayLeave(record);
        LeaveRequest leave = record.getLeaveRequest();
        if (leave != null && record.getCheckInAt().isBefore(leave.getEndAt()) && now.isAfter(leave.getStartAt())) {
            throw new ConflictException(ErrorCode.ATTENDANCE_OVERLAPS_LEAVE, "Worked time overlaps approved leave");
        }
        record.setCheckOutAt(now);
        attendanceDayCalculator.applyPunches(record);
        record.setStatus(AttendanceStatus.PRESENT);
        // Giờ ra đổi → quyết định tăng ca cũ không còn đúng; ngày quay lại hàng chờ nếu vẫn làm dư giờ.
        record.setOvertimeMinutes(0);
        record.setOvertimeMultiplier(BigDecimal.ONE);
        record.setOvertimeApprovedByAccount(null);
        record.setOvertimeApprovedAt(null);
        record.setUpdatedByAccount(account);
        return attendanceMapper.toResponse(attendanceRecordRepository.save(record));
    }

    private Optional<AttendanceRecord> checkedIn(Long employeeId, LocalDate date) {
        return attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(employeeId, date)
            .filter(record -> record.getCheckInAt() != null);
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new ConflictException(ErrorCode.ATTENDANCE_WEB_CHECK_IN_DISABLED, "Web check-in is turned off");
        }
    }

    /** Off until the company profile exists and someone switches it on. */
    private boolean enabled() {
        return companyProfileRepository.findById(COMPANY_PROFILE_ID)
            .map(CompanyProfile::getWebCheckInEnabled)
            .orElse(false);
    }

    private static Employee self(Account account) {
        Employee employee = account.getEmployee();
        if (employee == null || employee.getDeletedAt() != null) {
            throw new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND,
                "No employee is linked to account " + account.getId());
        }
        return employee;
    }

    private static void requireNotFullDayLeave(AttendanceRecord record) {
        if (FULL_DAY_LEAVE.contains(record.getStatus())) {
            throw new ConflictException(ErrorCode.ATTENDANCE_FULL_DAY_LEAVE,
                "Attendance on " + record.getWorkDate() + " is a full day of approved leave");
        }
    }
}
