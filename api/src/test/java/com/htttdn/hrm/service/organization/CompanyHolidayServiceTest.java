package com.htttdn.hrm.service.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.CompanyHolidayRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.service.payroll.PayrollPeriodInvalidator;

@ExtendWith(MockitoExtension.class)
class CompanyHolidayServiceTest {
    @Mock private CompanyHolidayRepository holidayRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private EmployeeAccessScopeService accessScopeService;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayslipItemRepository payslipItemRepository;

    @Test
    void changingHolidayInvalidatesCalculatedPayroll() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        PayrollPeriod period = PayrollPeriod.builder().id(1L)
            .status(PayrollPeriodStatus.CALCULATED)
            .calculatedAt(Instant.now())
            .calculatedByAccount(Account.builder().id(7L).build()).build();
        when(payrollPeriodRepository.findContainingDateForUpdate(date)).thenReturn(Optional.of(period));
        Payslip stale = Payslip.builder().id(5L).build();
        when(payslipRepository.findByPayrollPeriodId(1L)).thenReturn(List.of(stale));
        when(holidayRepository.save(any(CompanyHoliday.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(date, "Holiday");

        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
        assertNull(period.getCalculatedAt());
        assertNull(period.getCalculatedByAccount());
        verify(payslipItemRepository).deleteByPayslipId(5L);
        verify(payslipRepository).delete(stale);
    }

    @Test
    void approvedPayrollRejectsHolidayChange() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        when(payrollPeriodRepository.findContainingDateForUpdate(date)).thenReturn(Optional.of(
            PayrollPeriod.builder().id(1L).status(PayrollPeriodStatus.APPROVED).build()));

        assertThrows(ConflictException.class, () -> service().create(date, "Holiday"));
    }

    private CompanyHolidayService service() {
        return new CompanyHolidayService(holidayRepository, payrollPeriodRepository, accessScopeService,
            new PayrollPeriodInvalidator(payrollPeriodRepository, payslipRepository, payslipItemRepository));
    }
}
