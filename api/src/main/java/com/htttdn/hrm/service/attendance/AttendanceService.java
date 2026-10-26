package com.htttdn.hrm.service.attendance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.htttdn.hrm.config.ClockConfig;
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
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import lombok.RequiredArgsConstructor;

/**
 * Ghi công: tạo sẵn ngày công của tháng, ghi giờ vào/ra và HR chỉnh công. Áp đơn nghỉ đã duyệt nằm ở
 * {@link LeaveAttendanceService}, tăng ca ở {@link OvertimeService}, phần chỉ đọc ở
 * {@link AttendanceQueryService}; cách tính phút từ giờ vào/ra ở {@link AttendanceDayCalculator}.
 *
 * <p>Kiến thức nền: mỗi nhân viên có tối đa một {@link AttendanceRecord} mỗi ngày (unique
 * {@code uq_attendance_records_employee_date}). Lịch làm của ngày (ca, giờ bắt đầu/kết thúc, số phút
 * chuẩn) lấy từ phân công chính đang hiệu lực, qua {@link AttendanceCalendarService}; Chủ nhật và
 * ngày lễ công ty không có lịch. Mỗi ngày công có các con số:
 * <ul>
 *   <li>{@code scheduledMinutes} — phút làm chuẩn của ca;</li>
 *   <li>{@code workedMinutes} — phút thực có mặt, trừ giờ nghỉ giữa ca;</li>
 *   <li>{@code payableMinutes} — phút được trả lương, không vượt {@code scheduledMinutes} (phần
 *       làm dư là ứng viên tăng ca, do {@link OvertimeService} quyết định);</li>
 *   <li>{@code lateMinutes} / {@code earlyLeaveMinutes} — đi muộn / về sớm so với ca.</li>
 * </ul>
 * Tính lương ({@code PayslipCalculator}) đọc {@code payableMinutes} và từ chối ngày còn
 * {@code MISSING_PUNCH}. Ngày thuộc kỳ lương đã duyệt/trả/khóa thì không sửa được (xem
 * {@link AttendanceEditGuard}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AttendanceService {

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
    private final AttendanceDayCalculator attendanceDayCalculator;

    /**
     * Tạo sẵn ngày công trống ({@code MISSING_PUNCH}) cho mọi ngày có lịch làm trong tháng, của mọi
     * nhân viên có làm việc trong tháng. Ngày đã có bản ghi thì bỏ qua, nên chạy lại nhiều lần không
     * tạo trùng.
     *
     * <p>Chỉ HR phạm vi toàn công ty được chạy. Một nhân viên thiếu phân công/ca làm cả tháng thất
     * bại (cả transaction rollback); job chạy đêm thì dùng {@link #autoPrepareEmployee} để bỏ qua
     * từng người.
     *
     * @param year năm của tháng cần tạo
     * @param month tháng (1–12)
     * @return số ngày công mới được tạo
     * @throws com.htttdn.hrm.exception.ForbiddenException không có phạm vi COMPANY cho
     *         {@code attendance.manage} (→ 403)
     * @throws com.htttdn.hrm.exception.ConflictException tháng thuộc kỳ lương đã duyệt/trả/khóa
     *         (→ 409 {@code PAYROLL_PERIOD_LOCKED})
     * @throws BusinessException một nhân viên thiếu phân công chính hoặc ca vào ngày làm việc (→ 400)
     */
    @PreAuthorize("hasAuthority('attendance.manage')")
    public int prepareMonth(int year, int month) {
        employeeAccessScopeService.requireCompanyWide("attendance.manage");
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        attendanceEditGuard.requireEditable(from);
        int created = 0;
        // findEmployedDuring: chưa xóa mềm, đã vào làm trước cuối tháng và chưa nghỉ trước đầu tháng.
        for (Employee employee : employeeRepository.findEmployedDuring(from, to)) {
            created += prepareEmployee(employee, from, to);
        }
        return created;
    }

    /**
     * {@link #prepareMonth} for one employee, used by the background job: there is no caller to
     * authorize, and each employee runs in its own transaction so one with a missing assignment
     * or shift does not roll back the others.
     *
     * <p>Gọi từ {@link AttendanceAutoPrepareJob}: job bỏ qua nhân viên lỗi và dừng cả tháng khi gặp
     * {@code PAYROLL_PERIOD_LOCKED}.
     *
     * @param employeeId nhân viên cần tạo công
     * @param year năm của tháng cần tạo
     * @param month tháng (1–12)
     * @return số ngày công mới được tạo
     * @throws com.htttdn.hrm.exception.ConflictException tháng thuộc kỳ lương đã duyệt/trả/khóa
     * @throws ResourceNotFoundException không có nhân viên này
     * @throws BusinessException thiếu phân công chính hoặc ca vào một ngày làm việc
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int autoPrepareEmployee(Long employeeId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        attendanceEditGuard.requireEditable(from);
        // findById (không lọc xóa mềm): job chỉ truyền id lấy từ findEmployedDuring, vốn đã bỏ người bị xóa.
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + employeeId));
        return prepareEmployee(employee, from, to);
    }

    /**
     * Checks the whole schedule before saving, so a failing employee gets no records at all.
     *
     * <p>Chỉ xét phần tháng nhân viên còn làm (từ ngày vào làm đến ngày nghỉ việc). Mỗi ngày làm việc
     * của công ty (trừ Chủ nhật, ngày lễ) phải có lịch; thiếu thì báo lỗi kèm mã nhân viên, để HR biết
     * cần bổ sung phân công/ca cho ai.
     *
     * @return số ngày công mới được tạo
     */
    private int prepareEmployee(Employee employee, LocalDate from, LocalDate to) {
        int created = 0;
        LocalDate employedFrom = employee.getHireDate().isAfter(from) ? employee.getHireDate() : from;
        LocalDate employedTo = employee.getTerminationDate() != null
            && employee.getTerminationDate().isBefore(to) ? employee.getTerminationDate() : to;
        List<AttendanceCalendarService.ScheduledDay> scheduledDays;
        try {
            scheduledDays = attendanceCalendarService.scheduleFor(employee.getId(), employedFrom, employedTo);
        } catch (BusinessException error) {
            // scheduleFor ném lỗi khi phân công chính không có ca; ném lại kèm mã nhân viên.
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Cannot prepare attendance for employee " + employee.getEmployeeCode()
                    + " (ID " + employee.getId() + "): " + error.getMessage());
        }
        // scheduleFor bỏ qua ngày không có phân công chính → so với ngày làm việc của công ty để
        // phát hiện khoảng trống phân công.
        Set<LocalDate> scheduledDates = scheduledDays.stream()
            .map(AttendanceCalendarService.ScheduledDay::workDate).collect(Collectors.toSet());
        for (LocalDate date : attendanceCalendarService.companyWorkDates(employedFrom, employedTo)) {
            if (!scheduledDates.contains(date)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Cannot prepare attendance for employee " + employee.getEmployeeCode()
                        + " (ID " + employee.getId() + "): missing primary assignment and work shift on " + date);
            }
        }
        // Ngày đã có bản ghi (đã chấm giờ, đã áp đơn nghỉ, hay tạo ở lần chạy trước) giữ nguyên.
        Set<LocalDate> existingDates = attendanceRecordRepository
            .findByEmployeeIdAndWorkDateBetween(employee.getId(), from, to).stream()
            .map(AttendanceRecord::getWorkDate).collect(Collectors.toSet());
        for (AttendanceCalendarService.ScheduledDay day : scheduledDays) {
            if (existingDates.contains(day.workDate())) {
                continue;
            }
            attendanceRecordRepository.save(AttendanceDayCalculator.newRecord(employee, day));
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
     *
     * <p>Thứ tự: kiểm tra phạm vi → kiểm tra giờ hợp lệ → nhân viên còn làm ngày đó → kỳ lương chưa
     * chốt → ngày không bị đơn nghỉ chiếm → tính phút ({@link AttendanceDayCalculator#applyPunches}) → lưu.
     *
     * @param request nhân viên, ngày công, giờ vào/ra (Instant) và ghi chú
     * @return ngày công sau khi ghi
     * @throws com.htttdn.hrm.exception.ForbiddenException nhân viên ngoài phạm vi
     *         {@code attendance.manage} (→ 403; kiểm tra trước cả khi tìm nhân viên)
     * @throws BusinessException giờ vào không thuộc ngày công, giờ ra không sau giờ vào hoặc quá 24 giờ,
     *         nhân viên chưa vào làm/đã nghỉ, hoặc không có phân công chính ngày đó (→ 400)
     * @throws ResourceNotFoundException nhân viên không tồn tại hoặc đã xóa mềm (→ 404)
     * @throws com.htttdn.hrm.exception.ConflictException kỳ lương đã chốt, ngày nghỉ trọn ngày đã duyệt,
     *         hoặc giờ làm chồng lên khoảng nghỉ đã duyệt (→ 409)
     */
    @PreAuthorize("hasAuthority('attendance.manage')")
    public AttendanceRecordResponse recordPunches(RecordPunchesRequest request) {
        employeeAccessScopeService.requireEmployeeAccess(request.employeeId(), "attendance.manage");
        LocalDate workDate = request.workDate();
        Instant in = request.checkInAt();
        Instant out = request.checkOutAt();
        // Giờ vào đổi sang ngày theo giờ Việt Nam; giờ ra có thể sang ngày sau (ca đêm), tối đa 24 giờ.
        if (!LocalDate.ofInstant(in, ClockConfig.BUSINESS_ZONE).equals(workDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Check-in must fall on the work date " + workDate, "checkInAt");
        }
        if (!out.isAfter(in) || Duration.between(in, out).compareTo(Duration.ofDays(1)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Check-out must be after check-in and within 24 hours", "checkOutAt");
        }
        Employee employee = employeeRepository.getRequired(request.employeeId());
        if (employee.getHireDate().isAfter(workDate)
            || employee.getTerminationDate() != null && employee.getTerminationDate().isBefore(workDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee is not employed on " + workDate, "workDate");
        }
        attendanceEditGuard.requireEditable(workDate);

        // Khóa ghi (PESSIMISTIC_WRITE) bản ghi của ngày → hai lần ghi cùng lúc không đè nhau.
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
            // assignedDay lấy cả Chủ nhật, ngày lễ (khác scheduleFor) → ghi được giờ làm ngày nghỉ; khi đó
            // applyPunches cho payableMinutes = 0 và phần giờ đó chờ duyệt tăng ca.
            AttendanceCalendarService.ScheduledDay day = attendanceCalendarService
                .assignedDay(employee.getId(), workDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Employee has no primary assignment on " + workDate, "workDate"));
            record = AttendanceDayCalculator.newRecord(employee, day);
        }
        record.setCheckInAt(in);
        record.setCheckOutAt(out);
        attendanceDayCalculator.applyPunches(record);
        record.setStatus(AttendanceStatus.PRESENT);
        // Xóa quyết định tăng ca cũ → ngày quay lại hàng chờ của người duyệt nếu vẫn làm dư giờ.
        record.setOvertimeMinutes(0);
        record.setOvertimeMultiplier(BigDecimal.ONE);
        record.setOvertimeTaxExempt(false);
        record.setOvertimeApprovedByAccount(null);
        record.setOvertimeApprovedAt(null);
        record.setNote(Strings.trimToNull(request.note()));
        record.setUpdatedByAccount(currentAccountProvider.account());
        return attendanceMapper.toResponse(attendanceRecordRepository.save(record));
    }

    /**
     * HR sửa tay các con số của một ngày công (máy chấm công sai, quên chấm…) thay cho việc tính từ giờ
     * vào/ra. Ngày phải được chốt khỏi {@code MISSING_PUNCH} thì mới tính lương được.
     *
     * @param attendanceId ngày công cần sửa
     * @param request các con số mới, trạng thái và ghi chú
     * @return ngày công sau khi sửa
     * @throws ResourceNotFoundException không có ngày công này (→ 404 {@code ATTENDANCE_NOT_FOUND})
     * @throws com.htttdn.hrm.exception.ConflictException ngày thuộc kỳ lương đã chốt (→ 409)
     * @throws com.htttdn.hrm.exception.ForbiddenException nhân viên ngoài phạm vi (→ 403)
     * @throws BusinessException phút được trả vượt ca, vẫn để {@code MISSING_PUNCH}, {@code PRESENT}
     *         mà 0 phút làm, hoặc {@code UNAUTHORIZED_ABSENCE} mà có phút (→ 400)
     */
    @PreAuthorize("hasAuthority('attendance.manage')")
    public AttendanceRecordResponse adjust(Long attendanceId, AdjustAttendanceRequest request) {
        // Kiểm tra kỳ lương + khóa ghi trước, rồi mới kiểm tra phạm vi (cần biết ngày công của ai).
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
}
