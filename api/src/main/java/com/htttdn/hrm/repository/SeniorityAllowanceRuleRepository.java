package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.SeniorityAllowanceRule;

import jakarta.persistence.LockModeType;

public interface SeniorityAllowanceRuleRepository extends JpaRepository<SeniorityAllowanceRule, Long> {

    List<SeniorityAllowanceRule> findAllByOrderByEffectiveFromDescMinYearsAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT rule
        FROM SeniorityAllowanceRule rule
        WHERE rule.effectiveTo IS NULL
        ORDER BY rule.minYears ASC
        """)
    List<SeniorityAllowanceRule> findOpenForUpdate();

    @Query("""
        SELECT rule
        FROM SeniorityAllowanceRule rule
        WHERE rule.effectiveFrom <= :date
          AND (rule.effectiveTo IS NULL OR rule.effectiveTo >= :date)
          AND rule.minYears <= :years
          AND (rule.maxYears IS NULL OR rule.maxYears > :years)
        ORDER BY rule.effectiveFrom DESC, rule.minYears DESC
        """)
    List<SeniorityAllowanceRule> findEffectiveCandidates(
        @Param("years") int years,
        @Param("date") LocalDate date
    );

    default Optional<SeniorityAllowanceRule> findEffective(int years, LocalDate date) {
        List<SeniorityAllowanceRule> candidates = findEffectiveCandidates(years, date);
        if (candidates.size() > 1) {
            throw new IllegalStateException(
                "Multiple seniority allowance rules are effective for " + years + " years"
            );
        }
        return candidates.stream().findFirst();
    }
}
