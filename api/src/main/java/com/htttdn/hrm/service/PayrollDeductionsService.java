package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class PayrollDeductionsService {
    private final JdbcTemplate jdbc;
    private final EmployeeRepository employees;
    private final EmployeeAccessScopeService access;
    private final CurrentAccountProvider actor;
    private final PayrollPeriodRepository periods;
    private final PayslipRepository payslips;
    private final PayslipItemRepository items;

    public PayrollDeductionsService(JdbcTemplate jdbc, EmployeeRepository employees,
        EmployeeAccessScopeService access, CurrentAccountProvider actor, PayrollPeriodRepository periods,
        PayslipRepository payslips, PayslipItemRepository items) {
        this.jdbc = jdbc;
        this.employees = employees;
        this.access = access;
        this.actor = actor;
        this.periods = periods;
        this.payslips = payslips;
        this.items = items;
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<Profile> profiles(Long employeeId) {
        requireEmployee(employeeId, "compensation.read");
        return jdbc.query("""
            SELECT id, employee_id, effective_from, effective_to, tax_resident, social_insurance,
                   health_insurance, unemployment_insurance, insurance_salary, wage_region
            FROM employee_payroll_profiles WHERE employee_id = ? ORDER BY effective_from DESC
            """, (rs, row) -> new Profile(rs.getLong("id"), rs.getLong("employee_id"),
                rs.getDate("effective_from").toLocalDate(), dateOrNull(rs.getDate("effective_to")),
                rs.getBoolean("tax_resident"), rs.getBoolean("social_insurance"),
                rs.getBoolean("health_insurance"), rs.getBoolean("unemployment_insurance"),
                rs.getBigDecimal("insurance_salary"), rs.getShort("wage_region")), employeeId);
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Profile setProfile(Long employeeId, ProfileCommand command) {
        Employee employee = requireEmployee(employeeId, "compensation.manage");
        if (command == null || command.effectiveFrom() == null || command.taxResident() == null
            || command.socialInsurance() == null || command.healthInsurance() == null
            || command.unemploymentInsurance() == null || command.insuranceSalary() == null
            || command.insuranceSalary().signum() < 0 || command.wageRegion() == null
            || command.wageRegion() < 1 || command.wageRegion() > 4
            || command.effectiveFrom().isBefore(employee.getHireDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid payroll profile or effective date");
        }
        if (!command.taxResident()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Non-resident income tax requires a separate rule set; this payroll supports resident employees only");
        }
        if ((command.socialInsurance() || command.healthInsurance() || command.unemploymentInsurance())
            && command.insuranceSalary().signum() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "insuranceSalary is required when insurance is enabled");
        }
        invalidateAffectedPayroll(command.effectiveFrom());
        List<Profile> existing = profilesForUpdate(employeeId);
        Profile open = existing.stream().filter(profile -> profile.effectiveTo() == null).findFirst().orElse(null);
        if (open != null) {
            if (!command.effectiveFrom().isAfter(open.effectiveFrom())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "effectiveFrom must be after the current payroll profile start date");
            }
            jdbc.update("UPDATE employee_payroll_profiles SET effective_to = ? WHERE id = ?",
                Date.valueOf(command.effectiveFrom().minusDays(1)), open.id());
        }
        jdbc.update("""
            INSERT INTO employee_payroll_profiles(employee_id, effective_from, tax_resident,
                social_insurance, health_insurance, unemployment_insurance, insurance_salary,
                wage_region, created_by_account_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, employeeId, Date.valueOf(command.effectiveFrom()), command.taxResident(),
            command.socialInsurance(), command.healthInsurance(), command.unemploymentInsurance(),
            command.insuranceSalary(), command.wageRegion(), actor.accountId());
        return profilesForUpdate(employeeId).stream().filter(p -> p.effectiveFrom().equals(command.effectiveFrom()))
            .findFirst().orElseThrow();
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<Dependent> dependents(Long employeeId) {
        requireEmployee(employeeId, "compensation.read");
        return jdbc.query("""
            SELECT id, employee_id, full_name, identifier, effective_from, effective_to
            FROM employee_tax_dependents WHERE employee_id = ? ORDER BY effective_from DESC, id DESC
            """, (rs, row) -> new Dependent(rs.getLong("id"), rs.getLong("employee_id"),
                rs.getString("full_name"), rs.getString("identifier"),
                rs.getDate("effective_from").toLocalDate(), dateOrNull(rs.getDate("effective_to"))), employeeId);
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Dependent addDependent(Long employeeId, DependentCommand command) {
        Employee employee = requireEmployee(employeeId, "compensation.manage");
        if (command == null || command.fullName() == null || command.fullName().isBlank()
            || command.effectiveFrom() == null || command.effectiveFrom().isBefore(employee.getHireDate())
            || command.effectiveTo() != null && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid dependent or effective period");
        }
        Integer duplicate = jdbc.queryForObject("""
            SELECT COUNT(*) FROM employee_tax_dependents WHERE employee_id = ?
              AND lower(full_name) = lower(?)
              AND effective_from <= COALESCE(?, 'infinity'::date)
              AND (effective_to IS NULL OR effective_to >= ?)
            """, Integer.class, employeeId, command.fullName().trim(),
            command.effectiveTo() == null ? null : Date.valueOf(command.effectiveTo()),
            Date.valueOf(command.effectiveFrom()));
        if (duplicate != null && duplicate > 0) {
            throw new ConflictException(ErrorCode.CONFLICT, "Dependent already overlaps this effective period");
        }
        invalidateAffectedTaxPaymentPeriods(command.effectiveFrom(), command.effectiveTo());
        jdbc.update("""
            INSERT INTO employee_tax_dependents(employee_id, full_name, identifier, effective_from,
                effective_to, created_by_account_id) VALUES (?, ?, ?, ?, ?, ?)
            """, employeeId, command.fullName().trim(), blankToNull(command.identifier()),
            Date.valueOf(command.effectiveFrom()),
            command.effectiveTo() == null ? null : Date.valueOf(command.effectiveTo()), actor.accountId());
        return dependents(employeeId).stream().filter(d -> d.fullName().equals(command.fullName().trim())
            && d.effectiveFrom().equals(command.effectiveFrom())).findFirst().orElseThrow();
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Dependent endDependent(Long employeeId, Long dependentId, LocalDate effectiveTo) {
        requireEmployee(employeeId, "compensation.manage");
        Dependent current = dependents(employeeId).stream().filter(d -> d.id().equals(dependentId))
            .findFirst().orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                "Dependent not found: " + dependentId));
        if (effectiveTo == null || effectiveTo.isBefore(current.effectiveFrom())
            || current.effectiveTo() != null && !effectiveTo.isBefore(current.effectiveTo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid dependent end date");
        }
        invalidateAffectedTaxPaymentPeriods(effectiveTo.plusDays(1), current.effectiveTo());
        jdbc.update("UPDATE employee_tax_dependents SET effective_to = ? WHERE id = ?",
            Date.valueOf(effectiveTo), dependentId);
        return dependents(employeeId).stream().filter(d -> d.id().equals(dependentId)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public Deduction calculate(Long employeeId, LocalDate workMonthEnd, LocalDate paymentDate,
        BigDecimal taxableGrossPay, long unpaidDays) {
        Profile profile = jdbc.query("""
            SELECT id, employee_id, effective_from, effective_to, tax_resident, social_insurance,
                   health_insurance, unemployment_insurance, insurance_salary, wage_region
            FROM employee_payroll_profiles WHERE employee_id = ? AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
            """, (rs, row) -> new Profile(rs.getLong("id"), rs.getLong("employee_id"),
                rs.getDate("effective_from").toLocalDate(), dateOrNull(rs.getDate("effective_to")),
                rs.getBoolean("tax_resident"), rs.getBoolean("social_insurance"),
                rs.getBoolean("health_insurance"), rs.getBoolean("unemployment_insurance"),
                rs.getBigDecimal("insurance_salary"), rs.getShort("wage_region")),
            employeeId, Date.valueOf(workMonthEnd), Date.valueOf(workMonthEnd)).stream().findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": missing payroll profile on " + workMonthEnd));
        if (!profile.taxResident()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": non-resident tax calculation is not configured");
        }
        if (unpaidDays >= 14 && (profile.socialInsurance() || profile.healthInsurance()
            || profile.unemploymentInsurance())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": 14 or more unpaid workdays; HR must set the month's insurance participation explicitly");
        }
        InsuranceRule insurance = jdbc.query("""
            SELECT * FROM payroll_insurance_rules WHERE effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
            """, (rs, row) -> new InsuranceRule(rs.getLong("id"), rs.getBigDecimal("social_rate"),
                rs.getBigDecimal("health_rate"), rs.getBigDecimal("unemployment_rate"),
                rs.getBigDecimal("social_health_cap"), rs.getInt("unemployment_cap_multiplier"),
                rs.getBigDecimal("region_" + profile.wageRegion() + "_minimum")),
            Date.valueOf(workMonthEnd), Date.valueOf(workMonthEnd)).stream().findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Missing insurance rules for " + workMonthEnd));
        if ((profile.socialInsurance() || profile.healthInsurance() || profile.unemploymentInsurance())
            && profile.insuranceSalary().compareTo(insurance.regionMinimum()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": insuranceSalary is below region " + profile.wageRegion()
                    + " minimum on " + workMonthEnd);
        }
        BigDecimal socialBase = profile.insuranceSalary().min(insurance.socialHealthCap());
        BigDecimal unemploymentBase = profile.insuranceSalary().min(insurance.regionMinimum()
            .multiply(BigDecimal.valueOf(insurance.unemploymentCapMultiplier())));
        BigDecimal social = profile.socialInsurance() ? money(socialBase.multiply(insurance.socialRate())) : BigDecimal.ZERO;
        BigDecimal health = profile.healthInsurance() ? money(socialBase.multiply(insurance.healthRate())) : BigDecimal.ZERO;
        BigDecimal unemployment = profile.unemploymentInsurance()
            ? money(unemploymentBase.multiply(insurance.unemploymentRate())) : BigDecimal.ZERO;
        TaxRule tax = jdbc.query("""
            SELECT id, personal_deduction, dependent_deduction FROM payroll_tax_rules
            WHERE effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)
            """, (rs, row) -> new TaxRule(rs.getLong("id"), rs.getBigDecimal("personal_deduction"),
                rs.getBigDecimal("dependent_deduction")), Date.valueOf(paymentDate), Date.valueOf(paymentDate))
            .stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Missing resident income tax rules for payment date " + paymentDate));
        Integer dependentCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM employee_tax_dependents WHERE employee_id = ? AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
            """, Integer.class, employeeId, Date.valueOf(paymentDate), Date.valueOf(paymentDate));
        if (taxableGrossPay.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Taxable pay cannot be negative");
        }
        BigDecimal taxableIncome = taxableGrossPay.subtract(social).subtract(health).subtract(unemployment)
            .subtract(tax.personalDeduction())
            .subtract(tax.dependentDeduction().multiply(BigDecimal.valueOf(dependentCount == null ? 0 : dependentCount)))
            .max(BigDecimal.ZERO);
        List<TaxBracket> brackets = jdbc.query("""
            SELECT lower_bound, upper_bound, rate FROM payroll_tax_brackets
            WHERE tax_rule_id = ? ORDER BY lower_bound
            """, (rs, row) -> new TaxBracket(rs.getBigDecimal("lower_bound"),
                rs.getBigDecimal("upper_bound"), rs.getBigDecimal("rate")), tax.id());
        if (brackets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax rule has no brackets: " + tax.id());
        }
        return new Deduction(profile.id(), insurance.id(), tax.id(), socialBase, unemploymentBase,
            social, health, unemployment, taxableIncome, progressiveTax(taxableIncome, brackets));
    }

    static BigDecimal progressiveTax(BigDecimal taxableIncome, List<TaxBracket> brackets) {
        if (brackets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax rule has no brackets");
        }
        BigDecimal pit = BigDecimal.ZERO;
        BigDecimal expectedLower = BigDecimal.ZERO;
        for (TaxBracket bracket : brackets) {
            if (bracket.lowerBound().compareTo(expectedLower) != 0
                || bracket.rate().signum() < 0 || bracket.rate().compareTo(BigDecimal.ONE) > 0
                || bracket.upperBound() != null && bracket.upperBound().compareTo(bracket.lowerBound()) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax brackets have a gap or overlap");
            }
            BigDecimal upper = bracket.upperBound() == null ? taxableIncome : bracket.upperBound().min(taxableIncome);
            if (upper.compareTo(bracket.lowerBound()) > 0) {
                pit = pit.add(upper.subtract(bracket.lowerBound()).multiply(bracket.rate()));
            }
            if (bracket.upperBound() == null) {
                if (bracket != brackets.get(brackets.size() - 1)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Open tax bracket must be last");
                }
            } else {
                expectedLower = bracket.upperBound();
            }
        }
        if (brackets.get(brackets.size() - 1).upperBound() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax brackets must cover all income");
        }
        return money(pit);
    }

    private void invalidateAffectedPayroll(LocalDate effectiveFrom) {
        invalidate(periods.findAffectedPeriodsForUpdate(effectiveFrom, lockedStatuses()));
    }

    private void invalidateAffectedTaxPaymentPeriods(LocalDate effectiveFrom, LocalDate effectiveTo) {
        invalidate(periods.findAffectedPaymentPeriodsForUpdate(effectiveFrom, effectiveTo, lockedStatuses()));
    }

    private List<PayrollPeriodStatus> lockedStatuses() {
        return List.of(PayrollPeriodStatus.CALCULATED, PayrollPeriodStatus.APPROVED,
            PayrollPeriodStatus.PAID, PayrollPeriodStatus.LOCKED);
    }

    private void invalidate(List<PayrollPeriod> affected) {
        for (PayrollPeriod period : affected) {
            if (period.getStatus() != PayrollPeriodStatus.CALCULATED) {
                throw new ConflictException(ErrorCode.PAYROLL_PERIOD_LOCKED,
                    "Cannot change payroll data: period " + period.getYear() + "-" + period.getMonth()
                        + " is " + period.getStatus());
            }
            for (Payslip slip : payslips.findByPayrollPeriodId(period.getId())) {
                items.deleteByPayslipId(slip.getId());
                payslips.delete(slip);
            }
            period.setStatus(PayrollPeriodStatus.DRAFT);
            period.setCalculatedByAccount(null);
            period.setCalculatedAt(null);
            period.setUpdatedAt(java.time.Instant.now());
        }
    }

    private List<Profile> profilesForUpdate(Long employeeId) {
        return jdbc.query("""
            SELECT id, employee_id, effective_from, effective_to, tax_resident, social_insurance,
                   health_insurance, unemployment_insurance, insurance_salary, wage_region
            FROM employee_payroll_profiles WHERE employee_id = ? ORDER BY effective_from DESC FOR UPDATE
            """, (rs, row) -> new Profile(rs.getLong("id"), rs.getLong("employee_id"),
                rs.getDate("effective_from").toLocalDate(), dateOrNull(rs.getDate("effective_to")),
                rs.getBoolean("tax_resident"), rs.getBoolean("social_insurance"),
                rs.getBoolean("health_insurance"), rs.getBoolean("unemployment_insurance"),
                rs.getBigDecimal("insurance_salary"), rs.getShort("wage_region")), employeeId);
    }

    private Employee requireEmployee(Long employeeId, String permission) {
        Employee employee = employees.findByIdAndDeletedAtIsNull(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND,
                "Employee not found: " + employeeId));
        access.requireEmployeeAccess(employeeId, permission);
        return employee;
    }

    private static LocalDate dateOrNull(Date date) { return date == null ? null : date.toLocalDate(); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    public record ProfileCommand(LocalDate effectiveFrom, Boolean taxResident, Boolean socialInsurance,
        Boolean healthInsurance, Boolean unemploymentInsurance, BigDecimal insuranceSalary, Short wageRegion) {}
    public record Profile(Long id, Long employeeId, LocalDate effectiveFrom, LocalDate effectiveTo,
        boolean taxResident, boolean socialInsurance, boolean healthInsurance,
        boolean unemploymentInsurance, BigDecimal insuranceSalary, short wageRegion) {}
    public record DependentCommand(String fullName, String identifier, LocalDate effectiveFrom, LocalDate effectiveTo) {}
    public record Dependent(Long id, Long employeeId, String fullName, String identifier,
        LocalDate effectiveFrom, LocalDate effectiveTo) {}
    public record EndDependentCommand(LocalDate effectiveTo) {}
    public record Deduction(Long profileId, Long insuranceRuleId, Long taxRuleId,
        BigDecimal insuranceSalaryBase, BigDecimal unemploymentInsuranceBase,
        BigDecimal social, BigDecimal health,
        BigDecimal unemployment, BigDecimal taxableIncome, BigDecimal incomeTax) {}
    private record InsuranceRule(Long id, BigDecimal socialRate, BigDecimal healthRate,
        BigDecimal unemploymentRate, BigDecimal socialHealthCap, int unemploymentCapMultiplier,
        BigDecimal regionMinimum) {}
    private record TaxRule(Long id, BigDecimal personalDeduction, BigDecimal dependentDeduction) {}
    static record TaxBracket(BigDecimal lowerBound, BigDecimal upperBound, BigDecimal rate) {}
}
