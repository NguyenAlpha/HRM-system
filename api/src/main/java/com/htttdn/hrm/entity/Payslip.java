package com.htttdn.hrm.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
@Table(name = "payslips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriod payrollPeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "employee_code_snapshot", nullable = false, length = 30)
    private String employeeCodeSnapshot;

    @Column(name = "employee_name_snapshot", nullable = false, length = 200)
    private String employeeNameSnapshot;

    @Column(name = "position_snapshot", nullable = false, length = 150)
    private String positionSnapshot;

    @Column(name = "work_location_snapshot", nullable = false, length = 150)
    private String workLocationSnapshot;

    @Column(name = "organization_unit_snapshot", nullable = false, length = 150)
    private String organizationUnitSnapshot;

    @Column(name = "contractual_base_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal contractualBaseSalary;

    @Column(name = "scheduled_work_minutes", nullable = false)
    private Integer scheduledWorkMinutes;

    @Column(name = "payable_work_minutes", nullable = false)
    private Integer payableWorkMinutes;

    @Column(name = "approved_overtime_minutes", nullable = false)
    private Integer approvedOvertimeMinutes;

    @Column(name = "base_salary_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalaryPay;

    @Column(name = "position_allowance_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal positionAllowancePay;

    @Column(name = "seniority_allowance_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal seniorityAllowancePay;

    @Column(name = "allowance_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal allowancePay;

    @Column(name = "overtime_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal overtimePay;

    @Column(name = "tax_exempt_overtime_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxExemptOvertimePay;

    @Column(name = "gross_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossPay;

    @Column(name = "insurance_salary_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal insuranceSalaryBase;

    @Column(name = "unemployment_insurance_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal unemploymentInsuranceBase;

    @Column(name = "employee_social_insurance", nullable = false, precision = 15, scale = 2)
    private BigDecimal employeeSocialInsurance;

    @Column(name = "employee_health_insurance", nullable = false, precision = 15, scale = 2)
    private BigDecimal employeeHealthInsurance;

    @Column(name = "employee_unemployment_insurance", nullable = false, precision = 15, scale = 2)
    private BigDecimal employeeUnemploymentInsurance;

    @Column(name = "taxable_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableIncome;

    @Column(name = "personal_income_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal personalIncomeTax;

    @Column(name = "tax_rule_id")
    private Long taxRuleId;

    @Column(name = "insurance_rule_id")
    private Long insuranceRuleId;

    @Column(name = "payroll_profile_id")
    private Long payrollProfileId;

    @Column(name = "net_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal netPay;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
