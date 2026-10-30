package com.htttdn.hrm.service.attendance;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.util.DayRange;

import lombok.RequiredArgsConstructor;

import static com.htttdn.hrm.service.attendance.AttendanceMinutes.workMinutes;

/**
 * Chấm công phía đơn nghỉ phép: tính số phút làm việc một khoảng nghỉ chiếm, và ghi đơn đã duyệt vào các
 * ngày công. Chỉ {@code LeaveRequestService} (và seeder) gọi, không có API riêng nên không có
 * {@code @PreAuthorize}: quyền đã được kiểm tra ở service đơn nghỉ.
 *
 * <p>Ghi giờ vào/ra và HR chỉnh công nằm ở {@link AttendanceService}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LeaveAttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceCalendarService attendanceCalendarService;
    private final AttendanceEditGuard attendanceEditGuard;
    private final AttendanceDayCalculator attendanceDayCalculator;

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
        // endAt là mốc mở: nghỉ tới đúng 00:00 thì ngày hôm đó không tính (DayRange lùi 1 nano trước khi lấy ngày).
        DayRange days = DayRange.covering(startAt, endAt);
        int minutes = 0;
        for (AttendanceCalendarService.ScheduledDay day
            : attendanceCalendarService.scheduleFor(employeeId, days.first().minusDays(1), days.last())) {
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
     *   <li>ngày đã chấm giờ (nghỉ một phần ngày) → tính lại phút qua
     *       {@link AttendanceDayCalculator#applyPunches};</li>
     *   <li>ngày chưa chấm giờ → nghỉ có lương ({@code EMPLOYER_PAID}) cộng phút nghỉ vào
     *       {@code payableMinutes}; nghỉ trọn ca thì đổi trạng thái theo loại nghỉ
     *       ({@link #leaveStatus}), nghỉ một phần thì vẫn {@code MISSING_PUNCH} chờ chấm phần còn lại.</li>
     * </ul>
     *
     * @param request đơn nghỉ đã duyệt, có {@code requestedMinutes} tính từ {@link #leaveMinutesFor}
     * @throws com.htttdn.hrm.exception.ConflictException kỳ lương đã chốt (→ 409 {@code PAYROLL_PERIOD_LOCKED}),
     *         ngày đã gắn đơn nghỉ khác (→ 409 {@code LEAVE_DAY_APPROVED}), ngày đang chấm dở, có giờ vào chưa
     *         có giờ ra (→ 409 {@code LEAVE_DAY_PUNCH_INCOMPLETE}), hoặc giờ đã làm chồng lên giờ nghỉ (→ 409
     *         {@code LEAVE_OVERLAPS_WORKED_TIME})
     * @throws BusinessException đơn dài hơn tổng giờ làm theo lịch hiện tại, VD lịch đã đổi sau khi nộp đơn
     *         (→ 400 {@code LEAVE_SCHEDULE_CHANGED})
     */
    public void applyApprovedLeave(LeaveRequest request) {
        Long employeeId = request.getEmployee().getId();
        DayRange days = DayRange.covering(request.getStartAt(), request.getEndAt());
        // Đếm ngược số phút của đơn: chia lần lượt cho từng ngày, hết thì các ngày sau không bị trừ.
        int remainingMinutes = request.getRequestedMinutes();
        for (AttendanceCalendarService.ScheduledDay day
            : attendanceCalendarService.scheduleFor(employeeId, days.first().minusDays(1), days.last())) {
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
                throw new ConflictException(ErrorCode.LEAVE_DAY_APPROVED,
                    "Attendance already references another leave request on " + day.workDate());
            }
            if (record != null && record.getCheckInAt() != null && record.getCheckOutAt() == null) {
                throw new ConflictException(ErrorCode.LEAVE_DAY_PUNCH_INCOMPLETE,
                    "Complete attendance before approving leave on " + day.workDate());
            }
            if (record != null && record.getCheckInAt() != null && record.getCheckOutAt() != null
                && record.getCheckInAt().isBefore(overlapEnd) && record.getCheckOutAt().isAfter(overlapStart)) {
                throw new ConflictException(ErrorCode.LEAVE_OVERLAPS_WORKED_TIME,
                    "Leave overlaps worked time on " + day.workDate());
            }
            if (record == null) {
                record = AttendanceDayCalculator.newRecord(request.getEmployee(), day);
            }
            record.setLeaveRequest(request);
            record.setLeaveMinutes(leaveMinutes);
            if (record.getCheckInAt() != null) {
                // The other part of the day was worked: late, early and payable now exclude the leave.
                attendanceDayCalculator.applyPunches(record);
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
            throw new BusinessException(ErrorCode.LEAVE_SCHEDULE_CHANGED,
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
}
