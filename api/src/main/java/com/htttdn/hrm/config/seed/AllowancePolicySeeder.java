package com.htttdn.hrm.config.seed;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.PositionAllowanceRule;
import com.htttdn.hrm.entity.SeniorityAllowanceRule;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.PositionAllowanceRuleRepository;
import com.htttdn.hrm.repository.SeniorityAllowanceRuleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Khởi tạo chính sách phụ cấp mẫu của công ty: phụ cấp chức vụ cố định theo tháng cho các chức danh
 * quản lý và chuyên môn, phụ cấp thâm niên theo % lương cơ bản. Đây là chính sách riêng của doanh
 * nghiệp chứ không phải dữ liệu pháp lý, nên HR đổi được qua API.
 *
 * <p>Quy tắc do Company Owner duyệt và hiệu lực từ ngày Owner vào làm, để mọi kỳ lương của dữ liệu
 * seed đều có phụ cấp. Seeder chạy sau {@link CompanyOwnerSeeder}; không có Company Owner thì bỏ qua.
 * Đã có quy tắc thâm niên thì bỏ qua cả nhóm thâm niên; chức danh đã có quy tắc thì bỏ qua chức danh
 * đó, nên quy tắc HR đã sửa không bị ghi đè.
 */
@Component
@Order(505)
@RequiredArgsConstructor
public class AllowancePolicySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AllowancePolicySeeder.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String SEED_NOTE = "Chính sách mẫu";

    /** Phụ cấp chức vụ theo tháng; chức danh không có trong danh sách thì không có phụ cấp. */
    static final Map<String, BigDecimal> POSITION_ALLOWANCES = Map.of(
        "DIRECTOR", new BigDecimal("10000000"),
        "OPERATIONS_MANAGER", new BigDecimal("5000000"),
        "BRANCH_MANAGER", new BigDecimal("4000000"),
        "WAREHOUSE_SUPERVISOR", new BigDecimal("3000000"),
        "TEAM_LEAD", new BigDecimal("2000000"),
        "PAYROLL_ACCOUNTANT", new BigDecimal("1000000"),
        "HR_SPECIALIST", new BigDecimal("1000000")
    );

    /** Phụ cấp thâm niên: từ {@code minYears} đến trước {@code maxYears} năm; dưới 1 năm không có. */
    static final List<SeniorityTier> SENIORITY_TIERS = List.of(
        new SeniorityTier(1, 3, new BigDecimal("3")),
        new SeniorityTier(3, 5, new BigDecimal("5")),
        new SeniorityTier(5, null, new BigDecimal("7"))
    );

    record SeniorityTier(int minYears, Integer maxYears, BigDecimal percentage) {
    }

    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final JobPositionRepository jobPositionRepository;
    private final PositionAllowanceRuleRepository positionAllowanceRuleRepository;
    private final SeniorityAllowanceRuleRepository seniorityAllowanceRuleRepository;
    private final CompanySeedProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        Account owner = accountRoleAssignmentRepository
            .findAccountsHoldingRole("COMPANY_OWNER", LocalDate.now(ZONE)).stream().findFirst().orElse(null);
        if (owner == null || owner.getEmployee() == null) {
            log.info("Allowance policy seed skipped: no Company Owner to approve the rules");
            return;
        }
        LocalDate effectiveFrom = owner.getEmployee().getHireDate();
        Instant createdAt = effectiveFrom.atStartOfDay(ZONE).toInstant();

        int positionCount = 0;
        for (Map.Entry<String, BigDecimal> allowance : POSITION_ALLOWANCES.entrySet()) {
            var position = jobPositionRepository.findByCodeAndDeletedAtIsNull(allowance.getKey()).orElse(null);
            if (position == null || positionAllowanceRuleRepository.existsByJobPositionId(position.getId())) {
                continue;
            }
            positionAllowanceRuleRepository.save(PositionAllowanceRule.builder()
                .jobPosition(position)
                .monthlyAmount(allowance.getValue())
                .effectiveFrom(effectiveFrom)
                .approvedByAccount(owner)
                .note(SEED_NOTE)
                .createdAt(createdAt)
                .build());
            positionCount++;
        }

        int seniorityCount = 0;
        if (seniorityAllowanceRuleRepository.count() == 0) {
            for (SeniorityTier tier : SENIORITY_TIERS) {
                seniorityAllowanceRuleRepository.save(SeniorityAllowanceRule.builder()
                    .minYears(tier.minYears())
                    .maxYears(tier.maxYears())
                    .percentage(tier.percentage())
                    .effectiveFrom(effectiveFrom)
                    .approvedByAccount(owner)
                    .note(SEED_NOTE)
                    .createdAt(createdAt)
                    .build());
                seniorityCount++;
            }
        }
        log.info("Allowance policy seed completed: {} position rules, {} seniority rules created",
            positionCount, seniorityCount);
    }
}
