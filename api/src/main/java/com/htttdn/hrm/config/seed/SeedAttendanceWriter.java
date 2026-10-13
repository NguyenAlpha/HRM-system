package com.htttdn.hrm.config.seed;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.service.AttendanceCalendarService;

/**
 * Ghi lịch sử chấm công mẫu cho nhân viên được seed: {@code PRESENT} cho mọi ngày làm việc trong
 * khoảng, phần lớn đúng giờ, cứ 10 ngày công có một ngày đi muộn 30 phút và một ngày về sớm 30 phút.
 *
 * <p>Khoảng 1/12 số ngày còn lại (1/3 trong tháng hiện tại) có tăng ca: về muộn 1–3 giờ sau ca. Tăng ca trước tháng hiện tại
 * đã được người duyệt của ngày đó quyết định (hệ số 150%, chịu thuế); tăng ca trong tháng hiện tại
 * chưa ai quyết định nên nằm trong danh sách chờ của người duyệt. Ngày tăng ca chọn ngẫu nhiên
 * nhưng cố định theo nhân viên, nên seed lại trên database trống cho cùng kết quả.
 */
@Component
class SeedAttendanceWriter {

    private static final int LATE_OR_EARLY_MINUTES = 30;
    private static final int OVERTIME_ONE_IN = 12;
    // More often in the current month, so the approver's queue has a few days to decide.
    private static final int CURRENT_MONTH_OVERTIME_ONE_IN = 3;
    private static final BigDecimal WEEKDAY_OVERTIME_MULTIPLIER = new BigDecimal("1.5");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceCalendarService attendanceCalendarService;

    SeedAttendanceWriter(
        AttendanceRecordRepository attendanceRecordRepository,
        AttendanceCalendarService attendanceCalendarService
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceCalendarService = attendanceCalendarService;
    }

    /**
     * Minutes follow {@code AttendanceService.recordPunches}: late minutes count after the shift's
     * grace period, and 30 minutes off either end of the shift stays clear of the lunch break.
     *
     * @param approverOn the account that decided the overtime of a past day (the employee's approver then)
     */
    int write(Employee employee, LocalDate from, LocalDate to, Function<LocalDate, Account> approverOn) {
        List<AttendanceCalendarService.ScheduledDay> days = attendanceCalendarService.scheduleFor(employee.getId(), from, to);
        Random random = new Random(employee.getId());
        YearMonth currentMonth = YearMonth.now(ZONE);
        for (int i = 0; i < days.size(); i++) {
            AttendanceCalendarService.ScheduledDay day = days.get(i);
            int lateBy = i % 10 == 3 ? LATE_OR_EARLY_MINUTES : 0;
            int earlyBy = i % 10 == 7 ? LATE_OR_EARLY_MINUTES : 0;
            boolean currentMonthDay = !YearMonth.from(day.workDate()).isBefore(currentMonth);
            // Drawn for every day so the choice of one day does not shift the others.
            int draw = random.nextInt(OVERTIME_ONE_IN * CURRENT_MONTH_OVERTIME_ONE_IN);
            boolean overtimeDay = draw % (currentMonthDay ? CURRENT_MONTH_OVERTIME_ONE_IN : OVERTIME_ONE_IN) == 0;
            int extraMinutes = (random.nextInt(5) + 2) * 30;
            int overtimeBy = overtimeDay && lateBy == 0 && earlyBy == 0 ? extraMinutes : 0;
            int payableMinutes = day.minutes() - lateBy - earlyBy;
            Instant checkOutAt = day.endAt().minus(Duration.ofMinutes(earlyBy)).plus(Duration.ofMinutes(overtimeBy));
            Instant recordedAt = checkOutAt;
            AttendanceRecord.AttendanceRecordBuilder record = AttendanceRecord.builder()
                .employee(employee)
                .workDate(day.workDate())
                .shift(day.shift())
                .scheduledStartAt(day.startAt())
                .scheduledEndAt(day.endAt())
                .scheduledMinutes(day.minutes())
                .checkInAt(day.startAt().plus(Duration.ofMinutes(lateBy)))
                .checkOutAt(checkOutAt)
                .workedMinutes(payableMinutes + overtimeBy)
                .payableMinutes(payableMinutes)
                .lateMinutes(Math.max(0, lateBy - day.shift().getGraceLateMinutes()))
                .earlyLeaveMinutes(earlyBy)
                .overtimeMinutes(0)
                .overtimeMultiplier(BigDecimal.ONE)
                .status(AttendanceStatus.PRESENT)
                .createdAt(recordedAt)
                .updatedAt(recordedAt);
            // Overtime of the current month waits for the approver; earlier overtime was decided the next day.
            if (overtimeBy > 0 && !currentMonthDay) {
                Instant decidedAt = recordedAt.plus(Duration.ofDays(1));
                record.overtimeMinutes(overtimeBy)
                    .overtimeMultiplier(WEEKDAY_OVERTIME_MULTIPLIER)
                    .overtimeApprovedByAccount(approverOn.apply(day.workDate()))
                    .overtimeApprovedAt(decidedAt)
                    .updatedAt(decidedAt);
            }
            attendanceRecordRepository.save(record.build());
        }
        return days.size();
    }
}
