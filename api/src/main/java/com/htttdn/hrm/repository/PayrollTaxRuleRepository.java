package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.PayrollTaxRule;

public interface PayrollTaxRuleRepository extends JpaRepository<PayrollTaxRule, Long> {

    /** At most one row matches: excl_payroll_tax_rules_overlap forbids overlapping periods. */
    @Query("""
        SELECT rule
        FROM PayrollTaxRule rule
        WHERE rule.effectiveFrom <= :date
          AND (rule.effectiveTo IS NULL OR rule.effectiveTo >= :date)
        """)
    Optional<PayrollTaxRule> findEffective(@Param("date") LocalDate date);
}
