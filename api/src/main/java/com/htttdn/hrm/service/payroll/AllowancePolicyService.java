package com.htttdn.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.PositionAllowanceRule;
import com.htttdn.hrm.entity.SeniorityAllowanceRule;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.PositionAllowanceRuleRepository;
import com.htttdn.hrm.repository.SeniorityAllowanceRuleRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AllowancePolicyService {


    private final PositionAllowanceRuleRepository positionRuleRepository;
    private final SeniorityAllowanceRuleRepository seniorityRuleRepository;
    private final JobPositionRepository jobPositionRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final PayrollPeriodInvalidator payrollPeriodInvalidator;
    private final Clock clock;

    @PreAuthorize("hasAuthority('compensation.manage')")
    public PositionAllowanceView setPositionAllowance(
        Long jobPositionId,
        SetPositionAllowanceCommand command
    ) {
        validateAmount(command.monthlyAmount(), "monthlyAmount");
        requireDate(command.effectiveFrom());
        JobPosition position = jobPositionRepository.getRequiredActiveForUpdate(jobPositionId);
        payrollPeriodInvalidator.invalidateFrom(command.effectiveFrom());

        List<PositionAllowanceRule> openRules = positionRuleRepository
            .findOpenByJobPositionIdForUpdate(jobPositionId);
        if (openRules.size() > 1) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Job position has multiple open allowance rules"
            );
        }
        openRules.stream().findFirst().ifPresent(current ->
            closeCurrentRule(current.getEffectiveFrom(), command.effectiveFrom(), current::setEffectiveTo)
        );
        positionRuleRepository.flush();

        PositionAllowanceRule rule = PositionAllowanceRule.builder()
            .jobPosition(position)
            .monthlyAmount(command.monthlyAmount())
            .effectiveFrom(command.effectiveFrom())
            .approvedByAccount(currentAccountProvider.account())
            .note(Strings.trimToNull(command.note()))
            .build();
        return toView(positionRuleRepository.save(rule));
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<PositionAllowanceView> listPositionAllowances(Long jobPositionId) {
        if (!jobPositionRepository.existsById(jobPositionId)) {
            throw new ResourceNotFoundException(
                ErrorCode.JOB_POSITION_NOT_FOUND,
                "Job position not found: " + jobPositionId
            );
        }
        return positionRuleRepository.findByJobPositionIdOrderByEffectiveFromDesc(jobPositionId)
            .stream()
            .map(this::toView)
            .toList();
    }

    /** Every job position with the allowance in effect today, for the allowance policy screen. */
    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<PositionAllowanceSummary> listPositionAllowanceSummaries() {
        LocalDate today = LocalDate.now(clock);
        return jobPositionRepository.findByDeletedAtIsNullOrderByTitleAsc().stream()
            .map(position -> {
                PositionAllowanceRule current = positionRuleRepository.findEffective(position.getId(), today).orElse(null);
                return new PositionAllowanceSummary(
                    position.getId(),
                    position.getCode(),
                    position.getTitle(),
                    Boolean.TRUE.equals(position.getIsActive()),
                    current == null ? null : current.getMonthlyAmount(),
                    current == null ? null : current.getEffectiveFrom()
                );
            })
            .toList();
    }

    /**
     * Thay cả bảng phụ cấp thâm niên từ {@code effectiveFrom}: mọi quy tắc đang mở đóng vào ngày hôm trước,
     * các khoảng mới được tạo. Khai báo cả bảng một lần, nên không năm thâm niên nào bị bỏ trống giữa chừng.
     *
     * @throws BusinessException các khoảng không liền nhau từ 0 năm đến không giới hạn (→ 400
     *         {@code VALIDATION_ERROR}, {@code field: tiers}), hoặc {@code effectiveFrom} không sau ngày bắt
     *         đầu của bảng đang áp dụng (→ 400, {@code field: effectiveFrom})
     * @throws ConflictException có kỳ lương đã duyệt từ {@code effectiveFrom} (→ 409 {@code PAYROLL_PERIOD_LOCKED})
     */
    @PreAuthorize("hasAuthority('compensation.manage')")
    public List<SeniorityAllowanceView> setSeniorityAllowances(SetSeniorityAllowancesCommand command) {
        requireDate(command.effectiveFrom());
        List<SeniorityTier> tiers = requireContiguousTiers(command.tiers());
        payrollPeriodInvalidator.invalidateFrom(command.effectiveFrom());
        for (SeniorityAllowanceRule current : seniorityRuleRepository.findOpenForUpdate()) {
            closeCurrentRule(current.getEffectiveFrom(), command.effectiveFrom(), current::setEffectiveTo);
        }
        seniorityRuleRepository.flush();

        Account approver = currentAccountProvider.account();
        String note = Strings.trimToNull(command.note());
        return tiers.stream()
            .map(tier -> seniorityRuleRepository.save(SeniorityAllowanceRule.builder()
                .minYears(tier.minYears())
                .maxYears(tier.maxYears())
                .percentage(tier.percentage())
                .effectiveFrom(command.effectiveFrom())
                .approvedByAccount(approver)
                .note(note)
                .build()))
            .map(this::toView)
            .toList();
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<SeniorityAllowanceView> listSeniorityAllowances() {
        return seniorityRuleRepository.findAllByOrderByEffectiveFromDescMinYearsAsc().stream()
            .map(this::toView)
            .toList();
    }

    /**
     * Các khoảng theo số năm tăng dần phải nối liền từ 0 tới khoảng cuối không giới hạn: không hở, không chồng,
     * nên mọi năm thâm niên có đúng một tỷ lệ (có thể là 0%).
     */
    private List<SeniorityTier> requireContiguousTiers(List<SeniorityTier> tiers) {
        List<SeniorityTier> sorted = tiers.stream().sorted(Comparator.comparing(SeniorityTier::minYears)).toList();
        int expectedMin = 0;
        for (int i = 0; i < sorted.size(); i++) {
            SeniorityTier tier = sorted.get(i);
            validateAmount(tier.percentage(), "tiers");
            boolean last = i == sorted.size() - 1;
            if (tier.minYears() != expectedMin || (tier.maxYears() == null) != last
                || tier.maxYears() != null && tier.maxYears() <= tier.minYears()) {
                throw validation("phải liền nhau từ 0 năm đến không giới hạn, không hở hay chồng nhau", "tiers");
            }
            expectedMin = last ? expectedMin : tier.maxYears();
        }
        return sorted;
    }

    private void validateAmount(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw validation(field + " must be zero or greater", field);
        }
    }

    private void requireDate(LocalDate effectiveFrom) {
        if (effectiveFrom == null) {
            throw validation("effectiveFrom is required", "effectiveFrom");
        }
    }

    private void closeCurrentRule(
        LocalDate currentEffectiveFrom,
        LocalDate nextEffectiveFrom,
        java.util.function.Consumer<LocalDate> closeAction
    ) {
        if (!nextEffectiveFrom.isAfter(currentEffectiveFrom)) {
            throw validation(
                "effectiveFrom must be after the current rule start date",
                "effectiveFrom"
            );
        }
        closeAction.accept(nextEffectiveFrom.minusDays(1));
    }

    private BusinessException validation(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, field);
    }

    private PositionAllowanceView toView(PositionAllowanceRule rule) {
        return new PositionAllowanceView(
            rule.getId(),
            rule.getJobPosition().getId(),
            rule.getMonthlyAmount(),
            rule.getEffectiveFrom(),
            rule.getEffectiveTo(),
            rule.getApprovedByAccount().getId(),
            rule.getNote(),
            rule.getCreatedAt()
        );
    }

    private SeniorityAllowanceView toView(SeniorityAllowanceRule rule) {
        return new SeniorityAllowanceView(
            rule.getId(),
            rule.getMinYears(),
            rule.getMaxYears(),
            rule.getPercentage(),
            rule.getEffectiveFrom(),
            rule.getEffectiveTo(),
            rule.getApprovedByAccount().getId(),
            rule.getNote(),
            rule.getCreatedAt()
        );
    }

    public record SetPositionAllowanceCommand(
        @NotNull @PositiveOrZero BigDecimal monthlyAmount,
        @NotNull LocalDate effectiveFrom,
        @Nullable String note
    ) {
    }

    public record SetSeniorityAllowancesCommand(
        @NotNull LocalDate effectiveFrom,
        @Nullable String note,
        @NotEmpty List<@Valid @NotNull SeniorityTier> tiers
    ) {
    }

    /** Một khoảng thâm niên [{@code minYears}, {@code maxYears}) năm; {@code maxYears} null → không giới hạn. */
    public record SeniorityTier(
        @NotNull @PositiveOrZero Integer minYears,
        @Nullable Integer maxYears,
        @NotNull @PositiveOrZero BigDecimal percentage
    ) {
    }

    public record PositionAllowanceView(
        Long id,
        Long jobPositionId,
        BigDecimal monthlyAmount,
        LocalDate effectiveFrom,
        @Nullable LocalDate effectiveTo,
        Long approvedByAccountId,
        @Nullable String note,
        Instant createdAt
    ) {
    }

    /** A job position and its allowance today; both allowance fields are null when it has none. */
    public record PositionAllowanceSummary(
        Long jobPositionId,
        String code,
        String title,
        boolean active,
        @Nullable BigDecimal currentMonthlyAmount,
        @Nullable LocalDate currentEffectiveFrom
    ) {
    }

    public record SeniorityAllowanceView(
        Long id,
        Integer minYears,
        @Nullable Integer maxYears,
        BigDecimal percentage,
        LocalDate effectiveFrom,
        @Nullable LocalDate effectiveTo,
        Long approvedByAccountId,
        @Nullable String note,
        Instant createdAt
    ) {
    }
}
