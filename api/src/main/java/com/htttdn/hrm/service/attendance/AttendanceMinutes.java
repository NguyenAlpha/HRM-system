package com.htttdn.hrm.service.attendance;

import java.time.Duration;
import java.time.Instant;

import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.WorkShift;

/**
 * Phép tính phút thuần trên các khoảng thời gian của một ca: phần nằm trong khung ca, trừ giờ nghỉ giữa
 * ca và trừ phần đã xin nghỉ. Không đọc database, nên dùng chung cho ghi giờ vào/ra
 * ({@link AttendanceDayCalculator}) và áp đơn nghỉ ({@link LeaveAttendanceService}).
 *
 * <p>Mọi khoảng đều nửa mở [from, to): mốc cuối không tính.
 */
final class AttendanceMinutes {

    private AttendanceMinutes() {
    }

    /** Scheduled work minutes in [from, to) covered by neither the shift break nor the leave. */
    static int uncoveredWorkMinutes(Instant scheduledStart, Instant scheduledEnd, WorkShift shift,
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
    static int workMinutes(Instant scheduledStart, Instant scheduledEnd, WorkShift shift,
        Instant from, Instant to) {
        return Math.max(0, overlapMinutes(from, to, scheduledStart, scheduledEnd)
            - breakMinutesWithin(scheduledStart, shift, from, to));
    }

    /**
     * Số phút của [from, to) rơi vào giờ nghỉ giữa ca. Giờ nghỉ được đặt theo khoảng cách từ đầu ca
     * ({@code floorMod} 24 giờ) → đúng cả với ca đêm có giờ nghỉ sau nửa đêm.
     */
    static int breakMinutesWithin(Instant scheduledStart, WorkShift shift, Instant from, Instant to) {
        if (shift.getBreakStartTime() == null) {
            return 0;
        }
        long offset = Math.floorMod(
            Duration.between(shift.getStartTime(), shift.getBreakStartTime()).toMinutes(), 24L * 60L);
        Instant breakStart = scheduledStart.plus(Duration.ofMinutes(offset));
        return overlapMinutes(from, to, breakStart, breakStart.plus(Duration.ofMinutes(shift.getBreakMinutes())));
    }

    /** Số phút chung của hai khoảng [from, to) và [windowStart, windowEnd); không giao nhau → 0. */
    static int overlapMinutes(Instant from, Instant to, Instant windowStart, Instant windowEnd) {
        Instant start = from.isAfter(windowStart) ? from : windowStart;
        Instant end = to.isBefore(windowEnd) ? to : windowEnd;
        return (int) Math.max(0, Duration.between(start, end).toMinutes());
    }
}
