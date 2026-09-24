package com.htttdn.hrm.config.seed;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.repository.CompanyProfileRepository;

/**
 * Khởi tạo hồ sơ doanh nghiệp duy nhất của hệ thống.
 *
 * <p>Schema giới hạn bảng {@code company_profile} chỉ có một bản ghi với ID bằng {@code 1}.
 * Seeder chỉ tạo dữ liệu khi bản ghi này chưa tồn tại và không ghi đè thông tin doanh nghiệp
 * đã được cập nhật trong quá trình vận hành.
 */
@Component
@Order(50)
public class CompanyProfileSeeder implements ApplicationRunner {

    static final short COMPANY_PROFILE_ID = 1;

    private static final Logger log = LoggerFactory.getLogger(CompanyProfileSeeder.class);

    private final CompanyProfileRepository companyProfileRepository;
    private final boolean enabled;

    public CompanyProfileSeeder(
        CompanyProfileRepository companyProfileRepository,
        @Value("${company.seed.enabled:true}") boolean enabled
    ) {
        this.companyProfileRepository = companyProfileRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (companyProfileRepository.existsById(COMPANY_PROFILE_ID)) {
            log.info("Company profile seed completed: existing profile retained");
            return;
        }

        Instant now = Instant.now();
        companyProfileRepository.save(CompanyProfile.builder()
            .id(COMPANY_PROFILE_ID)
            .code("HRM")
            .name("HRM Demo Company")
            .taxCode("0312345678")
            .phone("02812345678")
            .email("contact@hrm.local")
            .address("Ho Chi Minh City, Vietnam")
            .timezone("Asia/Ho_Chi_Minh")
            .createdAt(now)
            .updatedAt(now)
            .build());

        log.info("Company profile seed completed: profile created");
    }
}
