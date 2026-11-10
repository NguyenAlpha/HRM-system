package com.htttdn.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.response.payroll.PayslipCalculationResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.PayrollInsuranceRule;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.PayrollTaxBracket;
import com.htttdn.hrm.entity.PayrollTaxBracketId;
import com.htttdn.hrm.entity.PayrollTaxRule;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.EmployeeTaxDependentRepository;
import com.htttdn.hrm.repository.PayrollInsuranceRuleRepository;
import com.htttdn.hrm.repository.PayrollTaxBracketRepository;
import com.htttdn.hrm.repository.PayrollTaxRuleRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.service.attendance.AttendanceCalendarService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayslipCalculationServiceTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 9, 30);
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 10, 5);

    @Mock private PayrollQueryService payrollQueryService;
    @Mock private PayslipRepository payslipRepository;
    @Mock private AttendanceCalendarService attendanceCalendarService;
    @Mock private PayrollTaxRuleRepository taxRuleRepository;
    @Mock private PayrollTaxBracketRepository taxBracketRepository;
    @Mock private PayrollInsuranceRuleRepository insuranceRuleRepository;
    @Mock private EmployeeTaxDependentRepository dependentRepository;

    @Test
    void eachBracketTaxesOnlyThePartOfTheIncomeInsideIt() {
        stubPayslip(null, "12000000");
        when(dependentRepository.countEffective(2L, PAYMENT_DATE)).thenReturn(1L);

        PayslipCalculationResponse calculation = service().getCalculation(5L);

        assertEquals(26, calculation.companyWorkdays());
        assertEquals(1, calculation.dependentCount());
        assertEquals(0, new BigDecimal("0.08").compareTo(calculation.socialRate()));
        assertEquals(0, new BigDecimal("11000000").compareTo(calculation.personalDeduction()));
        List<PayslipCalculationResponse.TaxBracketStep> steps = calculation.taxBrackets();
        assertEquals(3, steps.size());
        assertEquals(0, new BigDecimal("5000000").compareTo(steps.get(0).taxedIncome()));
        assertEquals(0, new BigDecimal("250000").compareTo(steps.get(0).tax()));
        assertEquals(0, new BigDecimal("5000000").compareTo(steps.get(1).taxedIncome()));
        assertEquals(0, new BigDecimal("500000").compareTo(steps.get(1).tax()));
        assertEquals(0, new BigDecimal("2000000").compareTo(steps.get(2).taxedIncome()));
        assertEquals(0, new BigDecimal("300000").compareTo(steps.get(2).tax()));
        assertNull(steps.get(2).upperBound());
    }

    @Test
    void aLeaversDependentsAreCountedOnTheirLastWorkingDay() {
        LocalDate lastWorkingDay = LocalDate.of(2026, 9, 20);
        stubPayslip(lastWorkingDay, "0");
        when(dependentRepository.countEffective(2L, lastWorkingDay)).thenReturn(2L);

        PayslipCalculationResponse calculation = service().getCalculation(5L);

        assertEquals(2, calculation.dependentCount());
        calculation.taxBrackets().forEach(step -> assertEquals(0, BigDecimal.ZERO.compareTo(step.tax())));
    }

    @Test
    void whoeverCannotSeeThePayslipCannotSeeItsCalculation() {
        when(payrollQueryService.getPayslip(5L)).thenThrow(new ForbiddenException(
            com.htttdn.hrm.dto.response.common.ErrorCode.FORBIDDEN, "Payslip has not been approved"));

        assertThrows(ForbiddenException.class, () -> service().getCalculation(5L));
        verifyNoInteractions(payslipRepository);
    }

    private void stubPayslip(LocalDate terminationDate, String taxableIncome) {
        PayrollPeriod period = PayrollPeriod.builder()
            .periodStart(PERIOD_START).periodEnd(PERIOD_END).taxPaymentDate(PAYMENT_DATE).build();
        Payslip payslip = Payslip.builder()
            .id(5L)
            .payrollPeriod(period)
            .employee(Employee.builder().id(2L).terminationDate(terminationDate).build())
            .taxableIncome(new BigDecimal(taxableIncome))
            .taxRuleId(7L)
            .insuranceRuleId(8L)
            .build();
        when(payslipRepository.getRequired(5L)).thenReturn(payslip);
        when(attendanceCalendarService.companyWorkdayCount(PERIOD_START, PERIOD_END)).thenReturn(26L);

        PayrollInsuranceRule insurance = mock(PayrollInsuranceRule.class);
        when(insurance.getSocialRate()).thenReturn(new BigDecimal("0.08"));
        when(insurance.getHealthRate()).thenReturn(new BigDecimal("0.015"));
        when(insurance.getUnemploymentRate()).thenReturn(new BigDecimal("0.01"));
        when(insuranceRuleRepository.findById(8L)).thenReturn(Optional.of(insurance));

        PayrollTaxRule tax = mock(PayrollTaxRule.class);
        when(tax.getId()).thenReturn(7L);
        when(tax.getPersonalDeduction()).thenReturn(new BigDecimal("11000000"));
        when(tax.getDependentDeduction()).thenReturn(new BigDecimal("4400000"));
        when(taxRuleRepository.findById(7L)).thenReturn(Optional.of(tax));
        // Built before stubbing the repository: Mockito refuses to stub a mock inside another stubbing.
        List<PayrollTaxBracket> brackets = List.of(
            bracket("0", "5000000", "0.05"),
            bracket("5000000", "10000000", "0.10"),
            bracket("10000000", null, "0.15"));
        when(taxBracketRepository.findByIdTaxRuleIdOrderByIdLowerBound(7L)).thenReturn(brackets);
    }

    private static PayrollTaxBracket bracket(String lower, String upper, String rate) {
        PayrollTaxBracket bracket = mock(PayrollTaxBracket.class);
        when(bracket.getId()).thenReturn(new PayrollTaxBracketId(7L, new BigDecimal(lower)));
        when(bracket.getUpperBound()).thenReturn(upper == null ? null : new BigDecimal(upper));
        when(bracket.getRate()).thenReturn(new BigDecimal(rate));
        return bracket;
    }

    private PayslipCalculationService service() {
        return new PayslipCalculationService(payrollQueryService, payslipRepository, attendanceCalendarService,
            taxRuleRepository, taxBracketRepository, insuranceRuleRepository, dependentRepository);
    }
}
