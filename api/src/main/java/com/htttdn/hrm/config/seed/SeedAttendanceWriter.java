package com.htttdn.hrm.config.seed;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.service.AttendanceCalendarService;

/**
 * Ghi lịch sử chấm công mẫu cho nhân viên được seed: {@code PRESENT} cho mọi ngày làm việc trong
 * khoảng, phần lớn đúng giờ, cứ 10 ngày công có một ngày đi muộn 30 phút và một ngày về sớm 30 phút.
 */
@Component
class SeedAttendanceWriter {

    private static final int LATE_OR_EARLY_MINUTES = 30;

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
     */
    int write(Employee employee, LocalDate from, LocalDate to) {
        List<AttendanceCalendarService.ScheduledDay> days = attendanceCalendarService.scheduleFor(employee.getId(), from, to);
        for (int i = 0; i < days.size(); i++) {
            AttendanceCalendarService.ScheduledDay day = days.get(i);
            int lateBy = i % 10 == 3 ? LATE_OR_EARLY_MINUTES : 0;
            int earlyBy = i % 10 == 7 ? LATE_OR_EARLY_MINUTES : 0;
            int workedMinutes = day.minutes() - lateBy - earlyBy;
            Instant recordedAt = day.endAt();
            attendanceRecordRepository.save(AttendanceRecord.builder()
                .employee(employee)
                .workDate(day.workDate())
                .shift(day.shift())
                .scheduledStartAt(day.startAt())
                .scheduledEndAt(day.endAt())
                .scheduledMinutes(day.minutes())
                .checkInAt(day.startAt().plus(Duration.ofMinutes(lateBy)))
                .checkOutAt(day.endAt().minus(Duration.ofMinutes(earlyBy)))
                .workedMinutes(workedMinutes)
                .payableMinutes(workedMinutes)
                .lateMinutes(Math.max(0, lateBy - day.shift().getGraceLateMinutes()))
                .earlyLeaveMinutes(earlyBy)
                .overtimeMinutes(0)
                .overtimeMultiplier(BigDecimal.ONE)
                .status(AttendanceStatus.PRESENT)
                .createdAt(recordedAt)
                .updatedAt(recordedAt)
                .build());
        }
        return days.size();
    }
}
