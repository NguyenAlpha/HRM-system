package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.PositionAllowanceRule;

import jakarta.persistence.LockModeType;

public interface PositionAllowanceRuleRepository extends JpaRepository<PositionAllowanceRule, Long> {

    List<PositionAllowanceRule> findByJobPositionIdOrderByEffectiveFromDesc(Long jobPositionId);

    @Query("""
        SELECT rule
        FROM PositionAllowanceRule rule
        JOIN FETCH rule.jobPosition
        WHERE rule.jobPosition.id = :jobPositionId
          AND rule.effectiveFrom <= :date
          AND (rule.effectiveTo IS NULL OR rule.effectiveTo >= :date)
        ORDER BY rule.effectiveFrom DESC
        """)
    List<PositionAllowanceRule> findEffectiveCandidates(
        @Param("jobPositionId") Long jobPositionId,
        @Param("date") LocalDate date
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT rule
        FROM PositionAllowanceRule rule
        WHERE rule.jobPosition.id = :jobPositionId
          AND rule.effectiveTo IS NULL
        ORDER BY rule.effectiveFrom DESC
        """)
    List<PositionAllowanceRule> findOpenByJobPositionIdForUpdate(
        @Param("jobPositionId") Long jobPositionId
    );

    boolean existsByJobPositionId(Long jobPositionId);

    default Optional<PositionAllowanceRule> findEffective(Long jobPositionId, LocalDate date) {
        List<PositionAllowanceRule> candidates = findEffectiveCandidates(jobPositionId, date);
        if (candidates.size() > 1) {
            throw new IllegalStateException("Multiple allowance rules are effective for position " + jobPositionId);
        }
        return candidates.stream().findFirst();
    }
}
