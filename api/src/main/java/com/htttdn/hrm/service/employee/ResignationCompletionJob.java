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

import lombok.RequiredArgsConstructor;
import com.htttdn.hrm.config.ClockConfig;

/**
 * Kết thúc hợp đồng của các đơn nghỉ việc đã duyệt khi ngày làm việc cuối đã qua: lúc khởi động và
 * 00:10 mỗi đêm. Mỗi đơn chạy trong transaction riêng, nên một đơn lỗi không chặn các đơn khác.
 */
@Component
@EnableScheduling
@Order(1010)
@RequiredArgsConstructor
public class ResignationCompletionJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResignationCompletionJob.class);

    private final ResignationRequestService resignationRequestService;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        completeDue();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = ClockConfig.BUSINESS_ZONE_ID)
    public void completeDue() {
        int completed = 0;
        for (Long requestId : resignationRequestService.dueIds(LocalDate.now(clock))) {
            try {
                resignationRequestService.complete(requestId);
                completed++;
            } catch (RuntimeException error) {
                log.warn("Resignation request {} could not end the employment: {}", requestId, error.getMessage());
            }
        }
        if (completed > 0) {
            log.info("Resignation completion: {} employments ended", completed);
        }
    }
}
