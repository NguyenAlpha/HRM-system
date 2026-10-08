package com.htttdn.hrm.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leave_entitlement_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveEntitlementRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "base_days", nullable = false)
    private Integer baseDays;

    @Column(name = "seniority_block_years", nullable = false)
    private Integer seniorityBlockYears;

    @Column(name = "seniority_bonus_days", nullable = false)
    private Integer seniorityBonusDays;

    @Column(name = "source_reference", nullable = false, columnDefinition = "TEXT")
    private String sourceReference;
}
