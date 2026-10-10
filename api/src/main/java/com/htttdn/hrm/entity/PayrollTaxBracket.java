package com.htttdn.hrm.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One progressive income tax bracket of a {@link PayrollTaxRule}. Loaded by migration, read-only here.
 */
@Entity
@Immutable
@Table(name = "payroll_tax_brackets")
@Getter
@NoArgsConstructor
public class PayrollTaxBracket {

    @EmbeddedId
    private PayrollTaxBracketId id;

    @Column(name = "upper_bound", precision = 15, scale = 2)
    private BigDecimal upperBound;

    @Column(nullable = false, precision = 6, scale = 5)
    private BigDecimal rate;
}
