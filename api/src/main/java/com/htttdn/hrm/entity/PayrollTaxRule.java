package com.htttdn.hrm.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Personal and dependent deductions for resident income tax. Loaded by migration, read-only here.
 */
@Entity
@Immutable
@Table(name = "payroll_tax_rules")
@Getter
@NoArgsConstructor
public class PayrollTaxRule {

    @Id
    private Long id;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "personal_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal personalDeduction;

    @Column(name = "dependent_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal dependentDeduction;

    @Column(name = "source_reference", nullable = false, columnDefinition = "TEXT")
    private String sourceReference;
}
