package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeRepository;

/**
 * Prepares the current month's attendance (the same as {@code POST /api/attendance/prepare}) at
 * 00:05 on the 1st of every month, and once at startup in case the server was down at the turn
 * of the month. Re-running never creates duplicates.
 */
@Component
@EnableScheduling
@Order(1000)
@ConditionalOnProperty(name = "attendance.auto-prepare.enabled", havingValue = "true", matchIfMissing = true)
public class AttendanceAutoPrepareJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAutoPrepareJob.class);
    private static final String ZONE = "Asia/Ho_Chi_Minh";

    private final AttendanceService attendanceService;
    private final EmployeeRepository employeeRepository;

    public AttendanceAutoPrepareJob(AttendanceService attendanceService, EmployeeRepository employeeRepository) {
        this.attendanceService = attendanceService;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        prepareCurrentMonth();
    }

    @Scheduled(cron = "0 5 0 1 * *", zone = ZONE)
    public void prepareCurrentMonth() {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        int year = today.getYear();
        int month = today.getMonthValue();
        LocalDate from = today.withDayOfMonth(1);
        LocalDate to = today.withDayOfMonth(today.lengthOfMonth());
        int created = 0;
        int skipped = 0;
        try {
            for (Employee employee : employeeRepository.findEmployedDuring(from, to)) {
                try {
                    created += attendanceService.autoPrepareEmployee(employee.getId(), year, month);
                } catch (RuntimeException error) {
                    if (error instanceof ConflictException conflict
                        && conflict.getErrorCode() == ErrorCode.PAYROLL_PERIOD_LOCKED) {
                        log.info("Attendance auto-prepare skipped {}-{}: {}", year, month, error.getMessage());
                        return;
                    }
                    skipped++;
                    log.warn("Attendance auto-prepare skipped an employee: {}", error.getMessage());
                }
            }
        } catch (RuntimeException error) {
            log.error("Attendance auto-prepare failed for {}-{}", year, month, error);
            return;
        }
        log.info("Attendance auto-prepare for {}-{}: {} records created, {} employees skipped",
            year, month, created, skipped);
    }
}
