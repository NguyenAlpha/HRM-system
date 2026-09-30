package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.repository.WorkShiftRepository;

/**
 * Khởi tạo danh mục ca làm việc tối thiểu cho doanh nghiệp mới.
 *
 * <p>Seeder chỉ tạo code còn thiếu và không ghi đè cấu hình ca làm việc đã được doanh nghiệp
 * chỉnh sửa. Bản ghi đã xóa mềm không ngăn việc tạo lại ca mặc định vì ràng buộc code chỉ áp
 * dụng cho các bản ghi chưa bị xóa.
 */
@Component
@Order(80)
public class WorkShiftSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkShiftSeeder.class);

    static final List<WorkShiftDefinition> DEFAULT_SHIFTS = List.of(
        new WorkShiftDefinition(
            "OFFICE_DAY",
            "Ca hành chính",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            60,
            480,
            15,
            false
        )
    );

    private final WorkShiftRepository workShiftRepository;
    private final boolean enabled;

    public WorkShiftSeeder(
        WorkShiftRepository workShiftRepository,
        @Value("${company.seed.enabled:true}") boolean enabled
    ) {
        this.workShiftRepository = workShiftRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        int createdCount = 0;
        for (WorkShiftDefinition definition : DEFAULT_SHIFTS) {
            if (workShiftRepository.existsByCodeAndDeletedAtIsNull(definition.code())) {
                continue;
            }

            Instant now = Instant.now();
            workShiftRepository.save(WorkShift.builder()
                .code(definition.code())
                .name(definition.name())
                .startTime(definition.startTime())
                .endTime(definition.endTime())
                .breakMinutes(definition.breakMinutes())
                .standardWorkMinutes(definition.standardWorkMinutes())
                .graceLateMinutes(definition.graceLateMinutes())
                .crossesMidnight(definition.crossesMidnight())
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
            createdCount++;
        }

        log.info(
            "Work shift seed completed: {} created, {} ensured",
            createdCount,
            DEFAULT_SHIFTS.size()
        );
    }

    record WorkShiftDefinition(
        String code,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        int breakMinutes,
        int standardWorkMinutes,
        int graceLateMinutes,
        boolean crossesMidnight
    ) {
    }
}
