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
 * Employee insurance rates, caps and regional minimum wages. Loaded by migration, read-only here.
 */
@Entity
@Immutable
@Table(name = "payroll_insurance_rules")
@Getter
@NoArgsConstructor
public class PayrollInsuranceRule {

    @Id
    private Long id;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "social_rate", nullable = false, precision = 6, scale = 5)
    private BigDecimal socialRate;

    @Column(name = "health_rate", nullable = false, precision = 6, scale = 5)
    private BigDecimal healthRate;

    @Column(name = "unemployment_rate", nullable = false, precision = 6, scale = 5)
    private BigDecimal unemploymentRate;

    @Column(name = "social_health_cap", nullable = false, precision = 15, scale = 2)
    private BigDecimal socialHealthCap;

    @Column(name = "unemployment_cap_multiplier", nullable = false)
    private Integer unemploymentCapMultiplier;

    @Column(name = "region_1_minimum", nullable = false, precision = 15, scale = 2)
    private BigDecimal region1Minimum;

    @Column(name = "region_2_minimum", nullable = false, precision = 15, scale = 2)
    private BigDecimal region2Minimum;

    @Column(name = "region_3_minimum", nullable = false, precision = 15, scale = 2)
    private BigDecimal region3Minimum;

    @Column(name = "region_4_minimum", nullable = false, precision = 15, scale = 2)
    private BigDecimal region4Minimum;

    @Column(name = "source_reference", nullable = false, columnDefinition = "TEXT")
    private String sourceReference;

    public BigDecimal regionMinimum(short wageRegion) {
        return switch (wageRegion) {
            case 1 -> region1Minimum;
            case 2 -> region2Minimum;
            case 3 -> region3Minimum;
            case 4 -> region4Minimum;
            default -> throw new IllegalArgumentException("Unknown wage region: " + wageRegion);
        };
    }
}
