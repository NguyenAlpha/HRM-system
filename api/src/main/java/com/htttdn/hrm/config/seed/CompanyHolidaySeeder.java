package com.htttdn.hrm.config.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.repository.CompanyHolidayRepository;

import lombok.RequiredArgsConstructor;

/**
 * Khởi tạo lịch nghỉ lễ, Tết hưởng nguyên lương năm 2025 và 2026 theo Điều 112 Bộ luật Lao động
 * 2019: Tết Dương lịch 1 ngày, Tết Âm lịch 5 ngày, Giỗ Tổ Hùng Vương 1 ngày, 30/4, 1/5, Quốc khánh
 * 2 ngày. Lễ rơi vào Chủ nhật được nghỉ bù ngày làm việc kế tiếp.
 *
 * <p>Ngày Tết Âm lịch và ngày liền kề Quốc khánh theo lịch nghỉ của khối cơ quan nhà nước; doanh
 * nghiệp được chọn khác thì HR sửa trên trang chấm công. Một năm đã có ngày lễ nào thì seeder bỏ
 * qua cả năm đó, nên ngày HR đã xóa hoặc sửa không bị tạo lại.
 */
@Component
@Order(90)
@RequiredArgsConstructor
public class CompanyHolidaySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyHolidaySeeder.class);

    static final Map<Integer, List<HolidayDefinition>> HOLIDAYS = Map.of(
        2025, List.of(
            new HolidayDefinition(LocalDate.of(2025, 1, 1), "Tết Dương lịch"),
            new HolidayDefinition(LocalDate.of(2025, 1, 28), "Tết Nguyên đán (29 tháng Chạp)"),
            new HolidayDefinition(LocalDate.of(2025, 1, 29), "Tết Nguyên đán (mùng 1)"),
            new HolidayDefinition(LocalDate.of(2025, 1, 30), "Tết Nguyên đán (mùng 2)"),
            new HolidayDefinition(LocalDate.of(2025, 1, 31), "Tết Nguyên đán (mùng 3)"),
            new HolidayDefinition(LocalDate.of(2025, 2, 1), "Tết Nguyên đán (mùng 4)"),
            new HolidayDefinition(LocalDate.of(2025, 4, 7), "Giỗ Tổ Hùng Vương (10/3 âm lịch)"),
            new HolidayDefinition(LocalDate.of(2025, 4, 30), "Ngày Chiến thắng"),
            new HolidayDefinition(LocalDate.of(2025, 5, 1), "Ngày Quốc tế Lao động"),
            new HolidayDefinition(LocalDate.of(2025, 9, 1), "Quốc khánh (ngày liền kề)"),
            new HolidayDefinition(LocalDate.of(2025, 9, 2), "Quốc khánh")
        ),
        2026, List.of(
            new HolidayDefinition(LocalDate.of(2026, 1, 1), "Tết Dương lịch"),
            new HolidayDefinition(LocalDate.of(2026, 2, 16), "Tết Nguyên đán (30 tháng Chạp)"),
            new HolidayDefinition(LocalDate.of(2026, 2, 17), "Tết Nguyên đán (mùng 1)"),
            new HolidayDefinition(LocalDate.of(2026, 2, 18), "Tết Nguyên đán (mùng 2)"),
            new HolidayDefinition(LocalDate.of(2026, 2, 19), "Tết Nguyên đán (mùng 3)"),
            new HolidayDefinition(LocalDate.of(2026, 2, 20), "Tết Nguyên đán (mùng 4)"),
            // 10/3 âm lịch là Chủ nhật 26/4, nghỉ bù vào thứ Hai.
            new HolidayDefinition(LocalDate.of(2026, 4, 27), "Nghỉ bù Giỗ Tổ Hùng Vương (10/3 âm lịch)"),
            new HolidayDefinition(LocalDate.of(2026, 4, 30), "Ngày Chiến thắng"),
            new HolidayDefinition(LocalDate.of(2026, 5, 1), "Ngày Quốc tế Lao động"),
            new HolidayDefinition(LocalDate.of(2026, 9, 1), "Quốc khánh (ngày liền kề)"),
            new HolidayDefinition(LocalDate.of(2026, 9, 2), "Quốc khánh")
        )
    );

    private final CompanyHolidayRepository companyHolidayRepository;
    private final CompanySeedProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        int createdCount = 0;
        for (Map.Entry<Integer, List<HolidayDefinition>> year : new TreeMap<>(HOLIDAYS).entrySet()) {
            boolean yearHasHolidays = !companyHolidayRepository.findByHolidayDateBetweenOrderByHolidayDate(
                LocalDate.of(year.getKey(), 1, 1), LocalDate.of(year.getKey(), 12, 31)).isEmpty();
            if (yearHasHolidays) {
                continue;
            }
            Instant now = Instant.now();
            for (HolidayDefinition definition : year.getValue()) {
                companyHolidayRepository.save(CompanyHoliday.builder()
                    .holidayDate(definition.date())
                    .name(definition.name())
                    .createdAt(now)
                    .build());
                createdCount++;
            }
        }

        log.info("Company holiday seed completed: {} holidays created", createdCount);
    }

    record HolidayDefinition(LocalDate date, String name) { }
}
