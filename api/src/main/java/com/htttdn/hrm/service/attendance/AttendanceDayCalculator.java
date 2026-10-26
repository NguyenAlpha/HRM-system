package com.htttdn.hrm.service.attendance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;

import lombok.RequiredArgsConstructor;

import static com.htttdn.hrm.service.attendance.AttendanceMinutes.breakMinutesWithin;
import static com.htttdn.hrm.service.attendance.AttendanceMinutes.uncoveredWorkMinutes;
import static com.htttdn.hrm.service.attendance.AttendanceMinutes.workMinutes;

/**
 * Dựng một ngày công trống theo lịch và tính các con số phút của ngày từ giờ vào/ra. Dùng chung cho ghi
 * giờ ({@link AttendanceService}) và áp đơn nghỉ ({@link LeaveAttendanceService}).
 *
 * <p>Là {@code @Component} không có {@code @Transactional}: chỉ sửa bản ghi bên gọi đưa vào, chạy trong
 * transaction của bên gọi; bên gọi tự lưu.
 */
@Component
@RequiredArgsConstructor
public class AttendanceDayCalculator {

    private final AttendanceCalendarService attendanceCalendarService;

    /** Ngày công trống theo lịch làm: chưa chấm giờ, chưa có phút nào, trạng thái thiếu chấm công. */
    static AttendanceRecord newRecord(Employee employee, AttendanceCalendarService.ScheduledDay day) {
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

    /**
     * Derives late, worked, payable and early-leave minutes from the punches. The shift break and
     * an approved leave on the same day are not counted as missing time, so half a day of leave
     * plus the other half worked adds up to the full scheduled day.
     *
     * <p>Chưa có giờ ra thì chỉ tính đi muộn. Không đổi trạng thái ngày; bên gọi tự đặt.
     */
    void applyPunches(AttendanceRecord record) {
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
}
