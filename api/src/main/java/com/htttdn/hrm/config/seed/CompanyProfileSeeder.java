package com.htttdn.hrm.config.seed;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.entity.CompanyProfile;
import com.htttdn.hrm.repository.CompanyProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Khởi tạo hồ sơ doanh nghiệp duy nhất của hệ thống.
 *
 * <p>Schema giới hạn bảng {@code company_profile} chỉ có một bản ghi với ID bằng {@code 1}.
 * Seeder chỉ tạo dữ liệu khi bản ghi này chưa tồn tại và không ghi đè thông tin doanh nghiệp
 * đã được cập nhật trong quá trình vận hành. Logo mẫu lấy từ {@code seed/company-logo.png}.
 */
@Component
@Order(50)
@RequiredArgsConstructor
public class CompanyProfileSeeder implements ApplicationRunner {

    static final short COMPANY_PROFILE_ID = 1;
    private static final String LOGO = "seed/company-logo.png";

    private static final Logger log = LoggerFactory.getLogger(CompanyProfileSeeder.class);

    private final CompanyProfileRepository companyProfileRepository;
    private final CompanySeedProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.enabled()) {
            return;
        }

        if (companyProfileRepository.existsById(COMPANY_PROFILE_ID)) {
            log.info("Company profile seed completed: existing profile retained");
            return;
        }
        companyProfileRepository.save(CompanyProfile.builder()
            .id(COMPANY_PROFILE_ID)
            .code("HRM")
            .name("HRM Demo Company")
            .taxCode("0312345678")
            .phone("02812345678")
            .email("contact@hrm.local")
            .address("Ho Chi Minh City, Vietnam")
            .timezone(ClockConfig.BUSINESS_ZONE_ID)
            .logo(logo())
            .logoContentType("image/png")
            .logoUpdatedAt(Instant.now())
            .build());

        log.info("Company profile seed completed: profile created");
    }

    private static byte[] logo() throws IOException {
        try (InputStream input = new ClassPathResource(LOGO).getInputStream()) {
            return input.readAllBytes();
        }
    }
}
