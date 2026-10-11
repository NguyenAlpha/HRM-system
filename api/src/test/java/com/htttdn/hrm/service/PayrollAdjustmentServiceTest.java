package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollAdjustmentRequest;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.PayrollAdjustment;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.enums.PayrollAdjustmentType;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.PayrollAdjustmentRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollAdjustmentServiceTest {
    @Mock private PayrollAdjustmentRepository adjustmentRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @Test
    void addingAnAdjustmentSendsACalculatedPeriodBackToDraft() {
        PayrollPeriod period = period(PayrollPeriodStatus.CALCULATED);
        period.setCalculatedAt(Instant.now());
        period.setCalculatedByAccount(Account.builder().id(9L).build());
        when(payrollPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(employeeRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(employee(null)));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(adjustmentRepository.save(any(PayrollAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().create(1L, request(2L));

        assertEquals(PayrollAdjustmentType.BONUS, result.type());
        assertEquals("Thưởng Tết", result.description());
        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
        assertNull(period.getCalculatedAt());
        assertNull(period.getCalculatedByAccount());
    }

    @ParameterizedTest
    @EnumSource(value = PayrollPeriodStatus.class, names = { "APPROVED", "PAID", "LOCKED", "CANCELLED" })
    void adjustmentsAreFrozenOnceThePeriodLeavesCalculation(PayrollPeriodStatus status) {
        when(payrollPeriodRepository.findById(1L)).thenReturn(Optional.of(period(status)));

        assertThrows(ConflictException.class, () -> service().create(1L, request(2L)));
        assertThrows(ConflictException.class, () -> service().delete(1L, 5L));
    }

    @Test
    void employeeWhoLeftBeforeThePeriodCannotReceiveAnAdjustment() {
        when(payrollPeriodRepository.findById(1L)).thenReturn(Optional.of(period(PayrollPeriodStatus.DRAFT)));
        when(employeeRepository.findByIdAndDeletedAtIsNull(2L))
            .thenReturn(Optional.of(employee(LocalDate.of(2025, 12, 31))));

        BusinessException exception = assertThrows(BusinessException.class, () -> service().create(1L, request(2L)));
        assertEquals("employeeId", exception.getField());
    }

    @Test
    void deletingAnAdjustmentRemovesItFromTheDraftPeriod() {
        PayrollAdjustment adjustment = PayrollAdjustment.builder().id(5L).build();
        when(payrollPeriodRepository.findById(1L)).thenReturn(Optional.of(period(PayrollPeriodStatus.DRAFT)));
        when(adjustmentRepository.findByIdAndPayrollPeriodId(5L, 1L)).thenReturn(Optional.of(adjustment));

        service().delete(1L, 5L);

        verify(adjustmentRepository).delete(adjustment);
    }

    private static PayrollPeriod period(PayrollPeriodStatus status) {
        return PayrollPeriod.builder().id(1L).status(status)
            .periodStart(LocalDate.of(2026, 1, 1)).periodEnd(LocalDate.of(2026, 1, 31)).build();
    }

    private static Employee employee(LocalDate terminationDate) {
        return Employee.builder().id(2L).employeeCode("NV002").fullName("Nhân viên")
            .hireDate(LocalDate.of(2024, 1, 1)).terminationDate(terminationDate).build();
    }

    private static CreatePayrollAdjustmentRequest request(Long employeeId) {
        return new CreatePayrollAdjustmentRequest(employeeId, PayrollAdjustmentType.BONUS, " Thưởng Tết ",
            new BigDecimal("500000"));
    }

    private PayrollAdjustmentService service() {
        return new PayrollAdjustmentService(adjustmentRepository, payrollPeriodRepository, employeeRepository,
            accountRepository, currentAccountProvider, employeeAccessScopeService);
    }
}
