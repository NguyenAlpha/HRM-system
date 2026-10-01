package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.PositionAllowanceRuleRepository;
import com.htttdn.hrm.repository.SeniorityAllowanceRuleRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class AllowancePolicyService {

    private final PositionAllowanceRuleRepository positionRuleRepository;
    private final SeniorityAllowanceRuleRepository seniorityRuleRepository;
    private final JobPositionRepository jobPositionRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;

    public AllowancePolicyService(
        PositionAllowanceRuleRepository positionRuleRepository,
        SeniorityAllowanceRuleRepository seniorityRuleRepository,
        JobPositionRepository jobPositionRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider
    ) {
        this.positionRuleRepository = positionRuleRepository;
        this.seniorityRuleRepository = seniorityRuleRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.accountRepository = accountRepository;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public PositionAllowanceView setPositionAllowance(
        Long jobPositionId,
        SetPositionAllowanceCommand command
    ) {
        validateAmount(command.monthlyAmount(), "monthlyAmount");
        requireDate(command.effectiveFrom());
        JobPosition position = jobPositionRepository.findByIdForUpdate(jobPositionId)
            .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.JOB_POSITION_NOT_FOUND,
                "Active job position not found: " + jobPositionId
            ));

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
            .approvedByAccount(findCurrentAccount())
            .note(normalizeNullable(command.note()))
            .createdAt(Instant.now())
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

    @PreAuthorize("hasAuthority('compensation.manage')")
    public SeniorityAllowanceView setSeniorityAllowance(SetSeniorityAllowanceCommand command) {
        validateSeniorityCommand(command);
        List<SeniorityAllowanceRule> overlappingOpenRules = seniorityRuleRepository
            .findOpenForUpdate()
            .stream()
            .filter(rule -> rangesOverlap(
                rule.getMinYears(), rule.getMaxYears(), command.minYears(), command.maxYears()
            ))
            .toList();
        for (SeniorityAllowanceRule current : overlappingOpenRules) {
            closeCurrentRule(
                current.getEffectiveFrom(), command.effectiveFrom(), current::setEffectiveTo
            );
        }
        seniorityRuleRepository.flush();

        SeniorityAllowanceRule rule = SeniorityAllowanceRule.builder()
            .minYears(command.minYears())
            .maxYears(command.maxYears())
            .percentage(command.percentage())
            .effectiveFrom(command.effectiveFrom())
            .approvedByAccount(findCurrentAccount())
            .note(normalizeNullable(command.note()))
            .createdAt(Instant.now())
            .build();
        return toView(seniorityRuleRepository.save(rule));
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<SeniorityAllowanceView> listSeniorityAllowances() {
        return seniorityRuleRepository.findAllByOrderByEffectiveFromDescMinYearsAsc().stream()
            .map(this::toView)
            .toList();
    }

    private void validateSeniorityCommand(SetSeniorityAllowanceCommand command) {
        if (command.minYears() == null || command.minYears() < 0) {
            throw validation("minYears must be zero or greater", "minYears");
        }
        if (command.maxYears() != null && command.maxYears() <= command.minYears()) {
            throw validation("maxYears must be greater than minYears", "maxYears");
        }
        validateAmount(command.percentage(), "percentage");
        requireDate(command.effectiveFrom());
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

    private boolean rangesOverlap(
        int leftMin,
        Integer leftMax,
        int rightMin,
        Integer rightMax
    ) {
        int normalizedLeftMax = leftMax == null ? Integer.MAX_VALUE : leftMax;
        int normalizedRightMax = rightMax == null ? Integer.MAX_VALUE : rightMax;
        return leftMin < normalizedRightMax && rightMin < normalizedLeftMax;
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private BusinessException validation(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, field);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        BigDecimal monthlyAmount,
        LocalDate effectiveFrom,
        String note
    ) {
    }

    public record SetSeniorityAllowanceCommand(
        Integer minYears,
        Integer maxYears,
        BigDecimal percentage,
        LocalDate effectiveFrom,
        String note
    ) {
    }

    public record PositionAllowanceView(
        Long id,
        Long jobPositionId,
        BigDecimal monthlyAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long approvedByAccountId,
        String note,
        Instant createdAt
    ) {
    }

    public record SeniorityAllowanceView(
        Long id,
        Integer minYears,
        Integer maxYears,
        BigDecimal percentage,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long approvedByAccountId,
        String note,
        Instant createdAt
    ) {
    }
}
