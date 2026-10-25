package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.htttdn.hrm.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Kết thúc làm việc đã được HR lên lịch khi ngày làm việc cuối đã qua: lúc khởi động và 00:10 mỗi đêm,
 * cùng giờ với {@link ResignationCompletionJob}. Mỗi người chạy trong transaction riêng, nên một người
 * lỗi không chặn những người khác.
 */
@Component
@EnableScheduling
@Order(1011)
@RequiredArgsConstructor
public class ScheduledEmploymentEndJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEmploymentEndJob.class);
    private static final String ZONE = "Asia/Ho_Chi_Minh";

    private final EmployeeRepository employeeRepository;
    private final EmploymentLifecycleService employmentLifecycleService;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        completeDue();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = ZONE)
    public void completeDue() {
        int completed = 0;
        for (Long employeeId : employeeRepository.findScheduledEndsDue(LocalDate.now(clock))) {
            try {
                employmentLifecycleService.completeScheduledEnd(employeeId);
                completed++;
            } catch (RuntimeException error) {
                log.warn("Scheduled employment end of employee {} could not run: {}", employeeId, error.getMessage());
            }
        }
        if (completed > 0) {
            log.info("Scheduled employment end: {} employments ended", completed);
        }
    }
}
