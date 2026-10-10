package com.htttdn.hrm.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employee_payroll_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeePayrollProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "tax_resident", nullable = false)
    private Boolean taxResident;

    @Column(name = "social_insurance", nullable = false)
    private Boolean socialInsurance;

    @Column(name = "health_insurance", nullable = false)
    private Boolean healthInsurance;

    @Column(name = "unemployment_insurance", nullable = false)
    private Boolean unemploymentInsurance;

    @Column(name = "insurance_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal insuranceSalary;

    @Column(name = "wage_region", nullable = false)
    private Short wageRegion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private Account createdByAccount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
