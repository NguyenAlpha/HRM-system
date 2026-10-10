package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeePayrollProfileRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.repository.PayrollInsuranceRuleRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayrollTaxBracketRepository;
import com.htttdn.hrm.repository.PayrollTaxRuleRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class PayrollDeductionsIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PayrollDeductionsService service;
    @Autowired private EmployeeRepository employees;
    @Autowired private AccountRepository accounts;
    @Autowired private EmployeePayrollProfileRepository profiles;
    @Autowired private EmployeeTaxDependentRepository dependents;
    @Autowired private PayrollTaxRuleRepository taxRules;
    @Autowired private PayrollTaxBracketRepository taxBrackets;
    @Autowired private PayrollInsuranceRuleRepository insuranceRules;

    @Test
    void employeeInsuranceUsesSeparateCapsAndDependentDeduction() {
        Long employeeId = seedEmployee();
        LocalDate workEnd = LocalDate.of(2026, 10, 31);
        LocalDate payDate = LocalDate.of(2026, 11, 5);

        PayrollDeductionsService.Deduction result = service.calculate(employeeId, workEnd,
            payDate, new BigDecimal("40000000"), 0);

        assertEquals(new BigDecimal("50600000.00"), result.insuranceSalaryBase());
        assertEquals(new BigDecimal("60000000.00"), result.unemploymentInsuranceBase());
        assertEquals(new BigDecimal("4048000.00"), result.social());
        assertEquals(new BigDecimal("759000.00"), result.health());
        assertEquals(new BigDecimal("600000.00"), result.unemployment());
        assertEquals(new BigDecimal("12893000.00"), result.taxableIncome());
        assertEquals(new BigDecimal("789300.00"), result.incomeTax());
    }

    @Test
    void missingFutureTaxRuleAndFourteenUnpaidDaysStopCalculation() {
        Long employeeId = seedEmployee();
        assertThrows(BusinessException.class, () -> service.calculate(employeeId,
            LocalDate.of(2026, 10, 31), LocalDate.of(2027, 1, 5),
            new BigDecimal("40000000"), 0));
        assertThrows(BusinessException.class, () -> service.calculate(employeeId,
            LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 5),
            new BigDecimal("40000000"), 14));
    }

    @Test
    void dependentChangeUsesPaymentDateAndRejectsDuplicate() {
        Long employeeId = seedEmployee();
        PayrollPeriodRepository periods = mock(PayrollPeriodRepository.class);
        PayrollDeductionsService subject = subjectWith(periods);
        LocalDate paymentDate = LocalDate.of(2026, 11, 5);

        subject.addDependent(employeeId, new PayrollDeductionsService.DependentCommand(
            "Dependent Two", null, paymentDate, null));
        verify(periods).findAffectedPaymentPeriodsForUpdate(eq(paymentDate), isNull(), any());
        assertThrows(ConflictException.class, () -> subject.addDependent(employeeId,
            new PayrollDeductionsService.DependentCommand("Dependent Two", null, paymentDate, null)));
    }

    @Test
    void newPayrollProfileClosesTheOpenOneTheDayBefore() {
        Long employeeId = seedEmployee();
        PayrollDeductionsService subject = subjectWith(mock(PayrollPeriodRepository.class));

        PayrollDeductionsService.Profile created = subject.setProfile(employeeId,
            new PayrollDeductionsService.ProfileCommand(LocalDate.of(2026, 11, 1), true, true, true, true,
                new BigDecimal("20000000"), (short) 2));

        List<PayrollDeductionsService.Profile> history = profiles.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)
            .stream().map(profile -> new PayrollDeductionsService.Profile(profile.getId(), employeeId,
                profile.getEffectiveFrom(), profile.getEffectiveTo(), profile.getTaxResident(),
                profile.getSocialInsurance(), profile.getHealthInsurance(), profile.getUnemploymentInsurance(),
                profile.getInsuranceSalary(), profile.getWageRegion()))
            .toList();
        assertEquals(2, history.size());
        assertEquals(created, history.get(0));
        assertNull(history.get(0).effectiveTo());
        assertEquals(LocalDate.of(2026, 10, 31), history.get(1).effectiveTo());
    }

    private PayrollDeductionsService subjectWith(PayrollPeriodRepository periods) {
        CurrentAccountProvider actor = mock(CurrentAccountProvider.class);
        when(actor.accountId()).thenReturn(jdbc.queryForObject("SELECT id FROM accounts ORDER BY id LIMIT 1", Long.class));
        return new PayrollDeductionsService(profiles, dependents, taxRules, taxBrackets, insuranceRules,
            accounts, employees, mock(EmployeeAccessScopeService.class), actor, periods,
            mock(PayslipRepository.class), mock(PayslipItemRepository.class));
    }

    private Long seedEmployee() {
        Long actorId = jdbc.queryForObject("SELECT id FROM accounts ORDER BY id LIMIT 1", Long.class);
        Long employeeId = jdbc.queryForObject("""
            INSERT INTO employees(employee_code, full_name, hire_date, seniority_start_date, employment_status)
            VALUES (?, 'Tax Test', '2026-01-01', '2026-01-01', 'ACTIVE') RETURNING id
            """, Long.class, "TAX_" + UUID.randomUUID().toString().substring(0, 12));
        jdbc.update("""
            INSERT INTO employee_payroll_profiles(employee_id, effective_from, tax_resident,
                social_insurance, health_insurance, unemployment_insurance, insurance_salary,
                wage_region, created_by_account_id)
            VALUES (?, '2026-10-01', true, true, true, true, 60000000, 1, ?)
            """, employeeId, actorId);
        jdbc.update("""
            INSERT INTO employee_tax_dependents(employee_id, full_name, effective_from, created_by_account_id)
            VALUES (?, 'Dependent One', '2026-01-01', ?)
            """, employeeId, actorId);
        return employeeId;
    }
}
