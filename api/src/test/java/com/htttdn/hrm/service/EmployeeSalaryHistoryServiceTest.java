package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeSalaryHistoryServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeSalaryHistoryRepository salaryHistoryRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayslipItemRepository payslipItemRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @Test
    void setSalaryClosesCurrentRecordBeforeSavingReplacement() {
        Employee employee = Employee.builder()
            .id(1L)
            .hireDate(LocalDate.of(2024, 1, 1))
            .build();
        Account approver = Account.builder().id(9L).build();
        EmployeeSalaryHistory current = EmployeeSalaryHistory.builder()
            .employee(employee)
            .baseSalary(new BigDecimal("10000000"))
            .effectiveFrom(LocalDate.of(2025, 1, 1))
            .approvedByAccount(approver)
            .build();
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(salaryHistoryRepository.findOpenByEmployeeIdForUpdate(1L))
            .thenReturn(List.of(current));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(approver));
        when(salaryHistoryRepository.save(any(EmployeeSalaryHistory.class))).thenAnswer(invocation -> {
            EmployeeSalaryHistory saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        var result = service().setSalary(
            1L,
            new EmployeeSalaryHistoryService.SetSalaryCommand(
                new BigDecimal("12000000"),
                LocalDate.of(2026, 1, 1),
                "Annual review",
                null
            )
        );

        assertEquals(LocalDate.of(2025, 12, 31), current.getEffectiveTo());
        assertEquals(new BigDecimal("12000000"), result.baseSalary());
        assertEquals(9L, result.approvedByAccountId());
        verify(salaryHistoryRepository).flush();
    }

    @Test
    void setSalaryRejectsNonPositiveAmount() {
        Employee employee = Employee.builder()
            .id(1L)
            .hireDate(LocalDate.of(2024, 1, 1))
            .build();
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));

        assertThrows(
            BusinessException.class,
            () -> service().setSalary(
                1L,
                new EmployeeSalaryHistoryService.SetSalaryCommand(
                    BigDecimal.ZERO,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null
                )
            )
        );
    }

    @Test
    void setSalaryInvalidatesCalculatedPeriodAndRemovesStalePayslip() {
        Employee employee = Employee.builder().id(1L).hireDate(LocalDate.of(2024, 1, 1)).build();
        Account approver = Account.builder().id(9L).build();
        PayrollPeriod period = PayrollPeriod.builder()
            .id(7L).year((short) 2026).month((short) 1)
            .status(PayrollPeriodStatus.CALCULATED)
            .calculatedByAccount(approver).build();
        Payslip payslip = Payslip.builder().id(11L).build();
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(any(), any()))
            .thenReturn(List.of(period));
        when(salaryHistoryRepository.findOpenByEmployeeIdForUpdate(1L)).thenReturn(List.of());
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(approver));
        when(salaryHistoryRepository.save(any(EmployeeSalaryHistory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(payslipRepository.findByPayrollPeriodId(7L)).thenReturn(List.of(payslip));

        service().setSalary(1L, new EmployeeSalaryHistoryService.SetSalaryCommand(
            new BigDecimal("12000000"), LocalDate.of(2026, 1, 1), null, null));

        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
        assertNull(period.getCalculatedByAccount());
        assertNull(period.getCalculatedAt());
        verify(payslipItemRepository).deleteByPayslipId(11L);
        verify(payslipRepository).delete(payslip);
    }

    @Test
    void setSalaryRejectsChangeAffectingApprovedPeriod() {
        Employee employee = Employee.builder().id(1L).hireDate(LocalDate.of(2024, 1, 1)).build();
        PayrollPeriod period = PayrollPeriod.builder()
            .id(7L).year((short) 2026).month((short) 1)
            .status(PayrollPeriodStatus.APPROVED).build();
        when(employeeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(employee));
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(any(), any()))
            .thenReturn(List.of(period));

        assertThrows(ConflictException.class, () -> service().setSalary(1L,
            new EmployeeSalaryHistoryService.SetSalaryCommand(
                new BigDecimal("12000000"), LocalDate.of(2026, 1, 1), null, null)));
    }

    private EmployeeSalaryHistoryService service() {
        return new EmployeeSalaryHistoryService(
            employeeRepository,
            salaryHistoryRepository,
            payrollPeriodRepository,
            payslipRepository,
            payslipItemRepository,
            accountRepository,
            currentAccountProvider,
            employeeAccessScopeService
        );
    }
}
