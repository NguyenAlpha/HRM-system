package com.htttdn.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        when(jobPositionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(position));
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
        when(jobPositionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(position));
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(eq(SEPTEMBER), any()))
            .thenReturn(List.of(period(PayrollPeriodStatus.APPROVED)));

        assertThrows(ConflictException.class, () -> service().setPositionAllowance(3L,
            new AllowancePolicyService.SetPositionAllowanceCommand(new BigDecimal("5000000"), SEPTEMBER, null)));
        verify(positionRuleRepository, never()).save(any());
    }

    @Test
    void setSeniorityAllowanceRejectsChangeAffectingApprovedPeriod() {
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(eq(SEPTEMBER), any()))
            .thenReturn(List.of(period(PayrollPeriodStatus.LOCKED)));

        assertThrows(ConflictException.class, () -> service().setSeniorityAllowance(
            new AllowancePolicyService.SetSeniorityAllowanceCommand(0, 5, new BigDecimal("0.05"), SEPTEMBER, null)));
        verify(seniorityRuleRepository, never()).save(any(SeniorityAllowanceRule.class));
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
            new PayrollPeriodInvalidator(payrollPeriodRepository, payslipRepository, payslipItemRepository)
        );
    }
}
