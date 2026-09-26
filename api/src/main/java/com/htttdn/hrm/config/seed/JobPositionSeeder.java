package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.repository.JobPositionRepository;

/**
 * Khởi tạo danh mục vị trí công việc tối thiểu cho doanh nghiệp mới.
 *
 * <p>Vị trí công việc mô tả chức danh trong cơ cấu nhân sự, không phải role phân quyền.
 * Seeder chỉ tạo code còn thiếu và không ghi đè dữ liệu doanh nghiệp đã chỉnh sửa.
 */
@Component
@Order(70)
public class JobPositionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobPositionSeeder.class);

    private static final List<JobPositionDefinition> DEFAULT_POSITIONS = List.of(
        position("DIRECTOR", "Giám đốc", "Điều hành hoạt động toàn công ty", true),
        position("HR_SPECIALIST", "Chuyên viên nhân sự", "Thực hiện các nghiệp vụ nhân sự", false),
        position(
            "PAYROLL_ACCOUNTANT",
            "Kế toán tiền lương",
            "Tính toán và kiểm tra dữ liệu tiền lương",
            false
        ),
        position("OPERATIONS_MANAGER", "Quản lý vận hành", "Quản lý hoạt động vận hành", true),
        position("BRANCH_MANAGER", "Quản lý chi nhánh", "Quản lý hoạt động tại chi nhánh", true),
        position("WAREHOUSE_SUPERVISOR", "Giám sát kho", "Giám sát nhân sự và hoạt động tại kho", true),
        position("TEAM_LEAD", "Trưởng nhóm", "Quản lý công việc của một nhóm nhân viên", true),
        position("GENERAL_STAFF", "Nhân viên", "Vị trí nhân viên nghiệp vụ thông thường", false)
    );

    private final JobPositionRepository jobPositionRepository;
    private final boolean enabled;

    public JobPositionSeeder(
        JobPositionRepository jobPositionRepository,
        @Value("${company.seed.enabled:true}") boolean enabled
    ) {
        this.jobPositionRepository = jobPositionRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        int createdCount = 0;
        for (JobPositionDefinition definition : DEFAULT_POSITIONS) {
            if (jobPositionRepository.findByCodeAndDeletedAtIsNull(definition.code()).isPresent()) {
                continue;
            }

            Instant now = Instant.now();
            jobPositionRepository.save(JobPosition.builder()
                .code(definition.code())
                .title(definition.title())
                .description(definition.description())
                .isManagerial(definition.managerial())
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
            createdCount++;
        }

        log.info("Job position seed completed: {} created, {} ensured", createdCount, DEFAULT_POSITIONS.size());
    }

    private static JobPositionDefinition position(
        String code,
        String title,
        String description,
        boolean managerial
    ) {
        return new JobPositionDefinition(code, title, description, managerial);
    }

    private record JobPositionDefinition(
        String code,
        String title,
        String description,
        boolean managerial
    ) {
    }
}
