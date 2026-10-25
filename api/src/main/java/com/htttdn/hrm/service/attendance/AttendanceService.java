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
     *
     * <p>Thứ tự: kiểm tra phạm vi → kiểm tra giờ hợp lệ → nhân viên còn làm ngày đó → kỳ lương chưa
     * chốt → ngày không bị đơn nghỉ chiếm → tính phút ({@link #applyPunches}) → lưu.
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
        if (!LocalDate.ofInstant(in, ZONE).equals(workDate)) {
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
            record = newRecord(employee, day);
        }
        record.setCheckInAt(in);
        record.setCheckOutAt(out);
        applyPunches(record);
        record.setStatus(AttendanceStatus.PRESENT);
        // Xóa quyết định tăng ca cũ → ngày quay lại hàng chờ của người duyệt nếu vẫn làm dư giờ.
        record.setOvertimeMinutes(0);
        record.setOvertimeMultiplier(BigDecimal.ONE);
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
     *
     * <p>{@code LeaveRequestService} dùng con số này làm số phút của đơn nghỉ (0 phút → đơn bị từ
     * chối vì không chạm giờ làm nào).
     *
     * @param employeeId nhân viên xin nghỉ
     * @param startAt bắt đầu nghỉ
     * @param endAt kết thúc nghỉ (không tính chính thời điểm này)
     * @return tổng phút làm việc theo lịch nằm trong khoảng nghỉ
     */
    @Transactional(readOnly = true)
    public int leaveMinutesFor(Long employeeId, Instant startAt, Instant endAt) {
        LocalDate from = LocalDate.ofInstant(startAt, ZONE).minusDays(1);
        // endAt là mốc mở: nghỉ tới đúng 00:00 thì ngày hôm đó không tính → lùi 1 nano trước khi lấy ngày.
        LocalDate to = LocalDate.ofInstant(endAt.minusNanos(1), ZONE);
        int minutes = 0;
        for (AttendanceCalendarService.ScheduledDay day : attendanceCalendarService.scheduleFor(employeeId, from, to)) {
            minutes += Math.min(day.minutes(), workMinutes(day.startAt(), day.endAt(), day.shift(), startAt, endAt));
        }
        return minutes;
    }

    /**
     * Ghi một đơn nghỉ vừa được duyệt vào các ngày công nó chạm tới; gọi từ
     * {@code LeaveRequestService} khi duyệt đơn (và từ seeder), trong cùng transaction.
     *
     * <p>Mỗi ngày có lịch: tính số phút nghỉ của ngày (không vượt ca, không vượt số phút đơn còn lại),
     * gắn đơn vào ngày công rồi cập nhật:
     * <ul>
     *   <li>ngày đã chấm giờ (nghỉ một phần ngày) → tính lại phút qua {@link #applyPunches};</li>
     *   <li>ngày chưa chấm giờ → nghỉ có lương ({@code EMPLOYER_PAID}) cộng phút nghỉ vào
     *       {@code payableMinutes}; nghỉ trọn ca thì đổi trạng thái theo loại nghỉ
     *       ({@link #leaveStatus}), nghỉ một phần thì vẫn {@code MISSING_PUNCH} chờ chấm phần còn lại.</li>
     * </ul>
     *
     * @param request đơn nghỉ đã duyệt, có {@code requestedMinutes} tính từ {@link #leaveMinutesFor}
     * @throws com.htttdn.hrm.exception.ConflictException kỳ lương đã chốt, ngày đã gắn đơn nghỉ khác,
     *         ngày đang chấm dở (có giờ vào chưa có giờ ra) hoặc giờ đã làm chồng lên giờ nghỉ (→ 409)
     * @throws BusinessException đơn dài hơn tổng giờ làm theo lịch hiện tại (→ 400), VD lịch đã đổi
     *         sau khi nộp đơn
     */
    public void applyApprovedLeave(LeaveRequest request) {
        Long employeeId = request.getEmployee().getId();
        LocalDate from = LocalDate.ofInstant(request.getStartAt(), ZONE).minusDays(1);
        LocalDate to = LocalDate.ofInstant(request.getEndAt().minusNanos(1), ZONE);
        // Đếm ngược số phút của đơn: chia lần lượt cho từng ngày, hết thì các ngày sau không bị trừ.
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
                // Ngày đã gắn chính đơn này (áp lại lần nữa) → chỉ trừ phút, không ghi lại.
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

    /**
     * Trạng thái của ngày nghỉ trọn ca: công ty trả lương → {@code PAID_LEAVE}; BHXH trả → thai sản
     * hoặc ốm theo loại nghỉ; còn lại (không lương, hoặc BHXH với loại khác) → {@code UNPAID_LEAVE}.
     */
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
        // Đi muộn = phút làm từ đầu ca tới giờ vào, trừ phần đã xin nghỉ và trừ số phút ân hạn của ca.
        record.setLateMinutes(Math.max(0,
            uncoveredWorkMinutes(start, end, shift, leave, start, in) - shift.getGraceLateMinutes()));
        Instant out = record.getCheckOutAt();
        if (out == null) {
            return;
        }
        // Được trả = phút làm trong khung ca + phút nghỉ có lương, không vượt ca; làm ngoài khung ca không
        // tính ở đây mà chờ duyệt tăng ca.
        int paidLeaveMinutes = leave != null && leave.getSalaryTreatment() == LeaveSalaryTreatment.EMPLOYER_PAID
            ? record.getLeaveMinutes() : 0;
        int payableMinutes = Math.min(record.getScheduledMinutes(),
            workMinutes(start, end, shift, in, out) + paidLeaveMinutes);
        if (attendanceCalendarService.companyWorkDates(record.getWorkDate(), record.getWorkDate()).isEmpty()) {
            // Chủ nhật / ngày lễ: không có lương ngày thường, toàn bộ giờ làm chỉ được trả qua tăng ca.
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

    /**
     * Số phút của [from, to) rơi vào giờ nghỉ giữa ca. Giờ nghỉ được đặt theo khoảng cách từ đầu ca
     * ({@code floorMod} 24 giờ) → đúng cả với ca đêm có giờ nghỉ sau nửa đêm.
     */
    private static int breakMinutesWithin(Instant scheduledStart, WorkShift shift, Instant from, Instant to) {
        if (shift.getBreakStartTime() == null) {
            return 0;
        }
        long offset = Math.floorMod(
            Duration.between(shift.getStartTime(), shift.getBreakStartTime()).toMinutes(), 24L * 60L);
        Instant breakStart = scheduledStart.plus(Duration.ofMinutes(offset));
        return overlapMinutes(from, to, breakStart, breakStart.plus(Duration.ofMinutes(shift.getBreakMinutes())));
    }

    /** Số phút chung của hai khoảng [from, to) và [windowStart, windowEnd); không giao nhau → 0. */
    private static int overlapMinutes(Instant from, Instant to, Instant windowStart, Instant windowEnd) {
        Instant start = from.isAfter(windowStart) ? from : windowStart;
        Instant end = to.isBefore(windowEnd) ? to : windowEnd;
        return (int) Math.max(0, Duration.between(start, end).toMinutes());
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
