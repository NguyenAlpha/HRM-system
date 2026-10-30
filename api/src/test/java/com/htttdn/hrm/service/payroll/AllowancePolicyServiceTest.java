package com.htttdn.hrm.service.payroll;

import com.htttdn.hrm.config.ClockConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.PositionAllowanceRule;
import com.htttdn.hrm.entity.SeniorityAllowanceRule;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.repository.PositionAllowanceRuleRepository;
import com.htttdn.hrm.repository.SeniorityAllowanceRuleRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllowancePolicyServiceTest {

    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

    @Mock private PositionAllowanceRuleRepository positionRuleRepository;
    @Mock private SeniorityAllowanceRuleRepository seniorityRuleRepository;
    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayslipItemRepository payslipItemRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;

    @Test
    void setPositionAllowanceInvalidatesCalculatedPeriodAndRemovesStalePayslip() {
        Account approver = Account.builder().id(9L).build();
        JobPosition position = JobPosition.builder().id(3L).isActive(true).build();
        PayrollPeriod period = period(PayrollPeriodStatus.CALCULATED);
        period.setCalculatedByAccount(approver);
        Payslip payslip = Payslip.builder().id(11L).build();
        when(jobPositionRepository.getRequiredActiveForUpdate(3L)).thenReturn(position);
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(eq(SEPTEMBER), any()))
            .thenReturn(List.of(period));
        when(payslipRepository.findByPayrollPeriodId(7L)).thenReturn(List.of(payslip));
        when(positionRuleRepository.findOpenByJobPositionIdForUpdate(3L)).thenReturn(List.of());
        when(currentAccountProvider.account()).thenReturn(approver);
        when(positionRuleRepository.save(any(PositionAllowanceRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service().setPositionAllowance(3L, new AllowancePolicyService.SetPositionAllowanceCommand(
            new BigDecimal("5000000"), SEPTEMBER, null));

        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
        assertNull(period.getCalculatedByAccount());
        verify(payslipItemRepository).deleteByPayslipId(11L);
        verify(payslipRepository).delete(payslip);
    }

    @Test
    void setPositionAllowanceRejectsChangeAffectingApprovedPeriod() {
        JobPosition position = JobPosition.builder().id(3L).isActive(true).build();
        when(jobPositionRepository.getRequiredActiveForUpdate(3L)).thenReturn(position);
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(eq(SEPTEMBER), any()))
            .thenReturn(List.of(period(PayrollPeriodStatus.APPROVED)));

        assertThrows(ConflictException.class, () -> service().setPositionAllowance(3L,
            new AllowancePolicyService.SetPositionAllowanceCommand(new BigDecimal("5000000"), SEPTEMBER, null)));
        verify(positionRuleRepository, never()).save(any());
    }

    @Test
    void aSeniorityTableChangeAffectingAnApprovedPeriodIsRejected() {
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(eq(SEPTEMBER), any()))
            .thenReturn(List.of(period(PayrollPeriodStatus.LOCKED)));

        assertThrows(ConflictException.class, () -> service().setSeniorityAllowances(table(
            tier(0, 5, "5"), tier(5, null, "7"))));
        verify(seniorityRuleRepository, never()).save(any(SeniorityAllowanceRule.class));
    }

    @Test
    void aNewSeniorityTableReplacesEveryOpenRuleFromItsEffectiveDate() {
        Account approver = Account.builder().id(9L).build();
        SeniorityAllowanceRule everyone = SeniorityAllowanceRule.builder().minYears(0)
            .percentage(new BigDecimal("5")).effectiveFrom(LocalDate.of(2025, 1, 1)).approvedByAccount(approver).build();
        when(seniorityRuleRepository.findOpenForUpdate()).thenReturn(List.of(everyone));
        when(currentAccountProvider.account()).thenReturn(approver);
        when(seniorityRuleRepository.save(any(SeniorityAllowanceRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Split 0–∞ at 5%: below 5 years stays 5%, from 5 years it becomes 7%.
        var saved = service().setSeniorityAllowances(table(tier(5, null, "7"), tier(0, 5, "5")));

        assertEquals(SEPTEMBER.minusDays(1), everyone.getEffectiveTo());
        assertEquals(List.of(0, 5), saved.stream().map(AllowancePolicyService.SeniorityAllowanceView::minYears).toList());
        assertEquals(new BigDecimal("7"), saved.get(1).percentage());
    }

    @Test
    void aSeniorityTableMustCoverEveryYearOnceFromZero() {
        // A gap, an overlap, a start after 0, a bounded last tier and two open tiers each leave some years
        // without a rule, or with two.
        for (var tiers : List.of(
            List.of(tier(0, 5, "5"), tier(6, null, "7")),
            List.of(tier(0, 5, "5"), tier(3, null, "7")),
            List.of(tier(1, null, "5")),
            List.of(tier(0, 5, "5"), tier(5, 10, "7")),
            List.of(tier(0, null, "5"), tier(5, null, "7")))) {
            BusinessException error = assertThrows(BusinessException.class,
                () -> service().setSeniorityAllowances(new AllowancePolicyService.SetSeniorityAllowancesCommand(
                    SEPTEMBER, null, tiers)));
            assertEquals("tiers", error.getField());
        }
        verify(seniorityRuleRepository, never()).save(any(SeniorityAllowanceRule.class));
    }

    private static AllowancePolicyService.SetSeniorityAllowancesCommand table(AllowancePolicyService.SeniorityTier... tiers) {
        return new AllowancePolicyService.SetSeniorityAllowancesCommand(SEPTEMBER, null, List.of(tiers));
    }

    private static AllowancePolicyService.SeniorityTier tier(int minYears, Integer maxYears, String percentage) {
        return new AllowancePolicyService.SeniorityTier(minYears, maxYears, new BigDecimal(percentage));
    }

    private static PayrollPeriod period(PayrollPeriodStatus status) {
        return PayrollPeriod.builder().id(7L).year((short) 2026).month((short) 9).status(status).build();
    }

    private AllowancePolicyService service() {
        return new AllowancePolicyService(
            positionRuleRepository,
            seniorityRuleRepository,
            jobPositionRepository,
            currentAccountProvider,
            new PayrollPeriodInvalidator(payrollPeriodRepository, payslipRepository, payslipItemRepository),
            Clock.system(ClockConfig.BUSINESS_ZONE)
        );
    }
}
