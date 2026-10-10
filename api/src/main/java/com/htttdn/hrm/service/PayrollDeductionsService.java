package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeePayrollProfile;
import com.htttdn.hrm.entity.EmployeeTaxDependent;
import com.htttdn.hrm.entity.PayrollInsuranceRule;
import com.htttdn.hrm.entity.PayrollTaxRule;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeePayrollProfileRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.repository.PayrollInsuranceRuleRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayrollTaxBracketRepository;
import com.htttdn.hrm.repository.PayrollTaxRuleRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

@Service
@Transactional
public class PayrollDeductionsService {
    private final EmployeePayrollProfileRepository profileRepository;
    private final EmployeeTaxDependentRepository dependentRepository;
    private final PayrollTaxRuleRepository taxRules;
    private final PayrollTaxBracketRepository taxBrackets;
    private final PayrollInsuranceRuleRepository insuranceRules;
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final EmployeeAccessScopeService access;
    private final CurrentAccountProvider actor;
    private final PayrollPeriodRepository periods;
    private final PayslipRepository payslips;
    private final PayslipItemRepository items;

    public PayrollDeductionsService(EmployeePayrollProfileRepository profileRepository,
        EmployeeTaxDependentRepository dependentRepository, PayrollTaxRuleRepository taxRules,
        PayrollTaxBracketRepository taxBrackets, PayrollInsuranceRuleRepository insuranceRules,
        AccountRepository accounts, EmployeeRepository employees, EmployeeAccessScopeService access,
        CurrentAccountProvider actor, PayrollPeriodRepository periods, PayslipRepository payslips,
        PayslipItemRepository items) {
        this.profileRepository = profileRepository;
        this.dependentRepository = dependentRepository;
        this.taxRules = taxRules;
        this.taxBrackets = taxBrackets;
        this.insuranceRules = insuranceRules;
        this.accounts = accounts;
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
        return profileRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
            .map(PayrollDeductionsService::toProfile).toList();
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
        EmployeePayrollProfile open = profileRepository.findByEmployeeIdForUpdate(employeeId).stream()
            .filter(profile -> profile.getEffectiveTo() == null).findFirst().orElse(null);
        if (open != null) {
            if (!command.effectiveFrom().isAfter(open.getEffectiveFrom())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "effectiveFrom must be after the current payroll profile start date");
            }
            open.setEffectiveTo(command.effectiveFrom().minusDays(1));
            // Hibernate flushes inserts before updates; close the old period first or the
            // overlap exclusion constraint rejects the new profile.
            profileRepository.saveAndFlush(open);
        }
        EmployeePayrollProfile created = profileRepository.save(EmployeePayrollProfile.builder()
            .employee(employee)
            .effectiveFrom(command.effectiveFrom())
            .taxResident(command.taxResident())
            .socialInsurance(command.socialInsurance())
            .healthInsurance(command.healthInsurance())
            .unemploymentInsurance(command.unemploymentInsurance())
            .insuranceSalary(command.insuranceSalary())
            .wageRegion(command.wageRegion())
            .createdByAccount(accounts.getReferenceById(actor.accountId()))
            .createdAt(Instant.now())
            .build());
        return toProfile(created);
    }

    @PreAuthorize("hasAuthority('compensation.read')")
    @Transactional(readOnly = true)
    public List<Dependent> dependents(Long employeeId) {
        requireEmployee(employeeId, "compensation.read");
        return dependentRepository.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employeeId).stream()
            .map(PayrollDeductionsService::toDependent).toList();
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Dependent addDependent(Long employeeId, DependentCommand command) {
        Employee employee = requireEmployee(employeeId, "compensation.manage");
        if (command == null || command.fullName() == null || command.fullName().isBlank()
            || command.effectiveFrom() == null || command.effectiveFrom().isBefore(employee.getHireDate())
            || command.effectiveTo() != null && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid dependent or effective period");
        }
        if (dependentRepository.existsOverlappingPeriod(employeeId, command.fullName().trim(),
            command.effectiveFrom(), command.effectiveTo())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Dependent already overlaps this effective period");
        }
        invalidateAffectedTaxPaymentPeriods(command.effectiveFrom(), command.effectiveTo());
        EmployeeTaxDependent created = dependentRepository.save(EmployeeTaxDependent.builder()
            .employee(employee)
            .fullName(command.fullName().trim())
            .identifier(blankToNull(command.identifier()))
            .effectiveFrom(command.effectiveFrom())
            .effectiveTo(command.effectiveTo())
            .createdByAccount(accounts.getReferenceById(actor.accountId()))
            .createdAt(Instant.now())
            .build());
        return toDependent(created);
    }

    @PreAuthorize("hasAuthority('compensation.manage')")
    public Dependent endDependent(Long employeeId, Long dependentId, LocalDate effectiveTo) {
        requireEmployee(employeeId, "compensation.manage");
        EmployeeTaxDependent current = dependentRepository.findById(dependentId)
            .filter(dependent -> dependent.getEmployee().getId().equals(employeeId))
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                "Dependent not found: " + dependentId));
        if (effectiveTo == null || effectiveTo.isBefore(current.getEffectiveFrom())
            || current.getEffectiveTo() != null && !effectiveTo.isBefore(current.getEffectiveTo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid dependent end date");
        }
        invalidateAffectedTaxPaymentPeriods(effectiveTo.plusDays(1), current.getEffectiveTo());
        current.setEffectiveTo(effectiveTo);
        return toDependent(current);
    }

    @Transactional(readOnly = true)
    public Deduction calculate(Long employeeId, LocalDate workMonthEnd, LocalDate paymentDate,
        BigDecimal taxableGrossPay, long unpaidDays) {
        Profile profile = profileRepository.findEffective(employeeId, workMonthEnd)
            .map(PayrollDeductionsService::toProfile)
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
        PayrollInsuranceRule insurance = insuranceRules.findEffective(workMonthEnd)
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Missing insurance rules for " + workMonthEnd));
        if ((profile.socialInsurance() || profile.healthInsurance() || profile.unemploymentInsurance())
            && profile.insuranceSalary().compareTo(insurance.regionMinimum(profile.wageRegion())) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + ": insuranceSalary is below region " + profile.wageRegion()
                    + " minimum on " + workMonthEnd);
        }
        BigDecimal regionMinimum = insurance.regionMinimum(profile.wageRegion());
        BigDecimal socialBase = profile.insuranceSalary().min(insurance.getSocialHealthCap());
        BigDecimal unemploymentBase = profile.insuranceSalary().min(regionMinimum
            .multiply(BigDecimal.valueOf(insurance.getUnemploymentCapMultiplier())));
        BigDecimal social = profile.socialInsurance() ? money(socialBase.multiply(insurance.getSocialRate())) : BigDecimal.ZERO;
        BigDecimal health = profile.healthInsurance() ? money(socialBase.multiply(insurance.getHealthRate())) : BigDecimal.ZERO;
        BigDecimal unemployment = profile.unemploymentInsurance()
            ? money(unemploymentBase.multiply(insurance.getUnemploymentRate())) : BigDecimal.ZERO;
        PayrollTaxRule tax = taxRules.findEffective(paymentDate)
            .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Missing resident income tax rules for payment date " + paymentDate));
        long dependentCount = dependentRepository.countEffective(employeeId, paymentDate);
        if (taxableGrossPay.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Taxable pay cannot be negative");
        }
        BigDecimal taxableIncome = taxableGrossPay.subtract(social).subtract(health).subtract(unemployment)
            .subtract(tax.getPersonalDeduction())
            .subtract(tax.getDependentDeduction().multiply(BigDecimal.valueOf(dependentCount)))
            .max(BigDecimal.ZERO);
        List<TaxBracket> brackets = taxBrackets.findByIdTaxRuleIdOrderByIdLowerBound(tax.getId()).stream()
            .map(bracket -> new TaxBracket(bracket.getId().getLowerBound(), bracket.getUpperBound(), bracket.getRate()))
            .toList();
        if (brackets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax rule has no brackets: " + tax.getId());
        }
        return new Deduction(profile.id(), insurance.getId(), tax.getId(), socialBase, unemploymentBase,
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
            period.setUpdatedAt(Instant.now());
        }
    }

    private static Profile toProfile(EmployeePayrollProfile profile) {
        return new Profile(profile.getId(), profile.getEmployee().getId(), profile.getEffectiveFrom(),
            profile.getEffectiveTo(), profile.getTaxResident(), profile.getSocialInsurance(),
            profile.getHealthInsurance(), profile.getUnemploymentInsurance(), profile.getInsuranceSalary(),
            profile.getWageRegion());
    }

    private static Dependent toDependent(EmployeeTaxDependent dependent) {
        return new Dependent(dependent.getId(), dependent.getEmployee().getId(), dependent.getFullName(),
            dependent.getIdentifier(), dependent.getEffectiveFrom(), dependent.getEffectiveTo());
    }

    private Employee requireEmployee(Long employeeId, String permission) {
        Employee employee = employees.findByIdAndDeletedAtIsNull(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND,
                "Employee not found: " + employeeId));
        access.requireEmployeeAccess(employeeId, permission);
        return employee;
    }

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
    static record TaxBracket(BigDecimal lowerBound, BigDecimal upperBound, BigDecimal rate) {}
}
