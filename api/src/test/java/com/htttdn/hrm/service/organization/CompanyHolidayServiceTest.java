package com.htttdn.hrm.service.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
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
    @Mock private AttendanceRecordRepository attendanceRecordRepository;

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
        when(holidayRepository.saveAndFlush(any(CompanyHoliday.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

    @Test
    void newHolidayDropsPreparedDaysOfThatDate() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        when(holidayRepository.saveAndFlush(any(CompanyHoliday.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(date, "Holiday");

        verify(attendanceRecordRepository).deleteUntouchedOn(date);
    }

    @Test
    void holidayCannotBeAddedOverRecordedAttendance() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        when(attendanceRecordRepository.existsRecordedOn(date)).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class, () -> service().create(date, "Holiday"));
        assertEquals(ErrorCode.HOLIDAY_DAY_RECORDED, exception.getErrorCode());
        verify(attendanceRecordRepository, never()).deleteUntouchedOn(any());
    }

    @Test
    void holidayCannotBeRemovedOnceSomeoneWorkedIt() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        CompanyHoliday holiday = CompanyHoliday.builder().id(3L).holidayDate(date).name("Holiday").build();
        when(holidayRepository.getRequired(3L)).thenReturn(holiday);
        when(attendanceRecordRepository.existsRecordedOn(date)).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class, () -> service().delete(3L));
        assertEquals(ErrorCode.HOLIDAY_DAY_RECORDED, exception.getErrorCode());
        verify(holidayRepository, never()).delete(any());
    }

    @Test
    void duplicateHolidayHasItsOwnCode() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        when(holidayRepository.findByHolidayDate(date)).thenReturn(Optional.of(CompanyHoliday.builder().build()));

        ConflictException exception = assertThrows(ConflictException.class, () -> service().create(date, "Holiday"));
        assertEquals(ErrorCode.HOLIDAY_DATE_TAKEN, exception.getErrorCode());
        verify(payrollPeriodRepository, never()).findContainingDateForUpdate(any());
    }

    private CompanyHolidayService service() {
        return new CompanyHolidayService(holidayRepository, payrollPeriodRepository, accessScopeService,
            new PayrollPeriodInvalidator(payrollPeriodRepository, payslipRepository, payslipItemRepository),
            attendanceRecordRepository);
    }
}
