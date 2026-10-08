package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.LeaveEntitlementRule;

public interface LeaveEntitlementRuleRepository extends JpaRepository<LeaveEntitlementRule, Long> {

    @Query("""
        SELECT rule
        FROM LeaveEntitlementRule rule
        WHERE rule.effectiveFrom <= :date
          AND (rule.effectiveTo IS NULL OR rule.effectiveTo >= :date)
        """)
    Optional<LeaveEntitlementRule> findEffectiveAt(@Param("date") LocalDate date);
}
