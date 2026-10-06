package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.payroll.PayrollActionRequest;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.PositionAllowanceRule;
import com.htttdn.hrm.entity.SeniorityAllowanceRule;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.repository.PositionAllowanceRuleRepository;
import com.htttdn.hrm.repository.SeniorityAllowanceRuleRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.impl.PayrollServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceImplTest {

    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayslipItemRepository payslipItemRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    @Mock private PositionAllowanceRuleRepository positionAllowanceRuleRepository;
    @Mock private SeniorityAllowanceRuleRepository seniorityAllowanceRuleRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;
    @Mock private AttendanceCalendarService attendanceCalendarService;
    @Mock private PayrollDeductionsService payrollDeductionsService;

    @Test
    void employeeWithOnlyHolidayDatesRequiresManualPayrollReview() {
        LocalDate holiday = LocalDate.of(2026, 1, 1);
        PayrollPeriod period = PayrollPeriod.builder().id(1L).periodStart(holiday).periodEnd(holiday)
            .status(PayrollPeriodStatus.DRAFT).build();
        Employee employee = Employee.builder().id(2L).employeeCode("EMP002").hireDate(holiday)
            .terminationDate(holiday).build();
        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(Account.builder().id(9L).build()));
        when(employeeRepository.findEmployedDuring(holiday, holiday)).thenReturn(List.of(employee));

        BusinessException error = assertThrows(BusinessException.class,
            () -> service().calculate(1L, new PayrollActionRequest(holiday)));
        assertTrue(error.getMessage().contains("EMP002"));
        assertTrue(error.getMessage().contains("company workdays"));
    }

    @Test
    void missingEmployeeInputDoesNotMarkPeriodCalculated() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        PayrollPeriod period = PayrollPeriod.builder().id(1L).periodStart(start).periodEnd(end)
            .status(PayrollPeriodStatus.DRAFT).build();
        Employee employee = Employee.builder().id(2L).employeeCode("EMP002").hireDate(start).build();
        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(Account.builder().id(9L).build()));
        when(employeeRepository.findEmployedDuring(start, end))
            .thenReturn(List.of(employee));
        when(attendanceCalendarService.companyWorkDates(start, end)).thenReturn(List.of(end));
        when(attendanceCalendarService.scheduleFor(2L, start, end)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(end,
                WorkShift.builder().standardWorkMinutes(480).build(), Instant.now(), Instant.now(), 480)));
        when(employeeSalaryHistoryRepository.findEffective(2L, end)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
            () -> service().calculate(1L, new PayrollActionRequest(end)));
        assertTrue(error.getMessage().contains("EMP002"));
        assertTrue(error.getMessage().contains(end.toString()));
        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
    }

    @Test
    void employedEmployeeWithoutAssignmentIsNotSkipped() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate hireDate = LocalDate.of(2026, 10, 5);
        LocalDate assignedDate = hireDate.plusDays(1);
        PayrollPeriod period = PayrollPeriod.builder().id(1L).periodStart(start).periodEnd(end)
            .status(PayrollPeriodStatus.DRAFT).build();
        Employee employee = Employee.builder().id(1L).employeeCode("EMP001").hireDate(hireDate).build();
        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(Account.builder().id(9L).build()));
        when(employeeRepository.findEmployedDuring(start, end)).thenReturn(List.of(employee));
        when(attendanceCalendarService.companyWorkDates(hireDate, end))
            .thenReturn(List.of(hireDate, assignedDate));
        when(attendanceCalendarService.scheduleFor(1L, hireDate, end)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(assignedDate,
                WorkShift.builder().standardWorkMinutes(480).build(), Instant.now(), Instant.now(), 480)));

        BusinessException error = assertThrows(BusinessException.class,
            () -> service().calculate(1L, new PayrollActionRequest(end)));
        assertTrue(error.getMessage().contains("EMP001"));
        assertTrue(error.getMessage().contains("2026-10-05"));
        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
        verify(payslipRepository, org.mockito.Mockito.never()).save(any(Payslip.class));
    }

    @Test
    void missingScheduledDayDoesNotProduceFullSalary() {
        LocalDate first = LocalDate.of(2026, 1, 15);
        LocalDate second = first.plusDays(1);
        PayrollPeriod period = PayrollPeriod.builder().id(1L).periodStart(first).periodEnd(second)
            .status(PayrollPeriodStatus.DRAFT).build();
        Employee employee = Employee.builder().id(2L).employeeCode("EMP002").hireDate(first).build();
        AttendanceRecord attendance = AttendanceRecord.builder().workDate(first)
            .scheduledMinutes(480).payableMinutes(480).build();
        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(Account.builder().id(9L).build()));
        when(employeeRepository.findEmployedDuring(first, second))
            .thenReturn(List.of(employee));
        when(attendanceCalendarService.companyWorkDates(first, second)).thenReturn(List.of(first, second));
        when(employeeSalaryHistoryRepository.findEffective(2L, second))
            .thenReturn(Optional.of(EmployeeSalaryHistory.builder().build()));
        when(employeeAssignmentRepository.findCurrentPrimaryCandidates(2L, second))
            .thenReturn(List.of(EmployeeAssignment.builder().build()));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(2L, first, second))
            .thenReturn(List.of(attendance));
        WorkShift shift = WorkShift.builder().standardWorkMinutes(480).build();
        when(attendanceCalendarService.scheduleFor(2L, first, second)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(first, shift, Instant.now(), Instant.now(), 480),
            new AttendanceCalendarService.ScheduledDay(second, shift, Instant.now(), Instant.now(), 480)));

        assertThrows(BusinessException.class, () -> service().calculate(1L, new PayrollActionRequest(second)));
        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
    }

    @Test
    void attendanceChangedAfterCalculationBlocksApproval() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        Instant calculatedAt = Instant.now();
        PayrollPeriod period = PayrollPeriod.builder().id(1L).periodStart(start).periodEnd(end)
            .calculatedAt(calculatedAt).status(PayrollPeriodStatus.CALCULATED).build();
        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(attendanceRecordRepository.existsByWorkDateBetweenAndUpdatedAtAfter(start, end, calculatedAt))
            .thenReturn(true);

        assertThrows(ConflictException.class, () -> service().approve(1L, new PayrollActionRequest()));
        assertEquals(PayrollPeriodStatus.CALCULATED, period.getStatus());
    }

    @Test
    void calculateUsesNewSalaryAndAllowancePoliciesAndApprovedOvertimeOnly() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        LocalDate workedDate = LocalDate.of(2026, 1, 15);
        PayrollPeriod period = PayrollPeriod.builder()
            .id(1L)
            .periodStart(start)
            .periodEnd(end)
            .status(PayrollPeriodStatus.DRAFT)
            .build();
        Account actor = Account.builder().id(9L).build();
        Employee employee = Employee.builder()
            .id(2L)
            .employeeCode("EMP002")
            .hireDate(start)
            .fullName("Employee Two")
            .seniorityStartDate(LocalDate.of(2020, 1, 1))
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build();
        JobPosition position = JobPosition.builder().id(3L).code("LEAD").title("Team Lead").build();
        EmployeeAssignment assignment = EmployeeAssignment.builder()
            .employee(employee)
            .position(position)
            .organizationUnit(OrganizationUnit.builder().id(4L).name("Engineering").build())
            .workLocation(WorkLocation.builder().id(5L).name("Head Office").build())
            .build();
        EmployeeSalaryHistory salary = EmployeeSalaryHistory.builder()
            .employee(employee)
            .baseSalary(new BigDecimal("10000000"))
            .build();
        AttendanceRecord attendance = AttendanceRecord.builder()
            .scheduledMinutes(480)
            .payableMinutes(480)
            .overtimeMinutes(60)
            .overtimeMultiplier(new BigDecimal("1.5"))
            .overtimeApprovedByAccount(actor)
            .overtimeApprovedAt(Instant.now())
            .overtimeTaxExempt(true)
            .workDate(LocalDate.of(2026, 1, 15))
            .build();

        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(employeeRepository.findEmployedDuring(start, end))
            .thenReturn(List.of(employee));
        when(attendanceCalendarService.companyWorkDates(start, end)).thenReturn(List.of(workedDate));
        when(employeeSalaryHistoryRepository.findEffective(2L, workedDate)).thenReturn(Optional.of(salary));
        when(employeeAssignmentRepository.findCurrentPrimaryCandidates(2L, workedDate))
            .thenReturn(List.of(assignment));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(2L, start, end))
            .thenReturn(List.of(attendance));
        when(attendanceCalendarService.scheduleFor(2L, start, end)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(workedDate,
                WorkShift.builder().id(1L).standardWorkMinutes(480).build(),
                Instant.now(), Instant.now().plusSeconds(480 * 60), 480)
        ));
        when(attendanceCalendarService.companyWorkdayCount(start, end)).thenReturn(26L);
        when(positionAllowanceRuleRepository.findEffective(3L, workedDate)).thenReturn(Optional.of(
            PositionAllowanceRule.builder().monthlyAmount(new BigDecimal("1000000")).build()
        ));
        when(seniorityAllowanceRuleRepository.findEffective(6, workedDate)).thenReturn(Optional.of(
            SeniorityAllowanceRule.builder().percentage(new BigDecimal("5")).build()
        ));
        when(payslipRepository.save(any(Payslip.class))).thenAnswer(invocation -> {
            Payslip saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        stubNoDeductions();

        service().calculate(1L, new PayrollActionRequest(end));

        ArgumentCaptor<Payslip> payslipCaptor = ArgumentCaptor.forClass(Payslip.class);
        verify(payslipRepository).save(payslipCaptor.capture());
        Payslip payslip = payslipCaptor.getValue();
        assertEquals(new BigDecimal("384615.38"), payslip.getBaseSalaryPay());
        assertEquals(new BigDecimal("38461.54"), payslip.getPositionAllowancePay());
        assertEquals(new BigDecimal("19230.77"), payslip.getSeniorityAllowancePay());
        assertEquals(new BigDecimal("72115.38"), payslip.getOvertimePay());
        assertEquals(new BigDecimal("514423.07"), payslip.getGrossPay());
        assertEquals(new BigDecimal("72115.38"), payslip.getTaxExemptOvertimePay());
        verify(payrollDeductionsService).calculate(eq(2L), eq(end), eq(end),
            eq(new BigDecimal("442307.69")), eq(0L));
        assertEquals(60, payslip.getApprovedOvertimeMinutes());
        verify(payslipItemRepository).saveAll(any());
    }

    @Test
    void salaryChangesAndPartialUnpaidDayProratePayAndAllowance() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        LocalDate first = LocalDate.of(2026, 1, 15);
        LocalDate second = first.plusDays(1);
        PayrollPeriod period = PayrollPeriod.builder().id(1L).periodStart(start).periodEnd(end)
            .status(PayrollPeriodStatus.DRAFT).build();
        Employee employee = Employee.builder().id(2L).employeeCode("EMP002").fullName("Employee Two")
            .hireDate(start)
            .seniorityStartDate(LocalDate.of(2026, 1, 1)).build();
        JobPosition position = JobPosition.builder().id(3L).code("DEV").title("Developer").build();
        EmployeeAssignment assignment = EmployeeAssignment.builder().position(position)
            .workLocation(WorkLocation.builder().name("Office").build())
            .organizationUnit(OrganizationUnit.builder().name("Engineering").build()).build();
        WorkShift shift = WorkShift.builder().standardWorkMinutes(480).build();
        AttendanceRecord firstRecord = AttendanceRecord.builder().workDate(first)
            .scheduledMinutes(480).payableMinutes(480).build();
        AttendanceRecord secondRecord = AttendanceRecord.builder().workDate(second)
            .scheduledMinutes(480).payableMinutes(240).build();
        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(currentAccountProvider.accountId()).thenReturn(9L);
        when(accountRepository.findById(9L)).thenReturn(Optional.of(Account.builder().id(9L).build()));
        when(employeeRepository.findEmployedDuring(start, end)).thenReturn(List.of(employee));
        when(attendanceCalendarService.companyWorkDates(start, end)).thenReturn(List.of(first, second));
        when(attendanceCalendarService.scheduleFor(2L, start, end)).thenReturn(List.of(
            new AttendanceCalendarService.ScheduledDay(first, shift, Instant.now(), Instant.now(), 480),
            new AttendanceCalendarService.ScheduledDay(second, shift, Instant.now(), Instant.now(), 480)));
        when(attendanceCalendarService.companyWorkdayCount(start, end)).thenReturn(26L);
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(2L, start, end))
            .thenReturn(List.of(firstRecord, secondRecord));
        when(employeeSalaryHistoryRepository.findEffective(2L, first)).thenReturn(Optional.of(
            EmployeeSalaryHistory.builder().baseSalary(new BigDecimal("10000000")).build()));
        when(employeeSalaryHistoryRepository.findEffective(2L, second)).thenReturn(Optional.of(
            EmployeeSalaryHistory.builder().baseSalary(new BigDecimal("20000000")).build()));
        when(employeeAssignmentRepository.findCurrentPrimaryCandidates(2L, first)).thenReturn(List.of(assignment));
        when(employeeAssignmentRepository.findCurrentPrimaryCandidates(2L, second)).thenReturn(List.of(assignment));
        when(positionAllowanceRuleRepository.findEffective(3L, first)).thenReturn(Optional.of(
            PositionAllowanceRule.builder().monthlyAmount(new BigDecimal("1000000")).build()));
        when(positionAllowanceRuleRepository.findEffective(3L, second)).thenReturn(Optional.of(
            PositionAllowanceRule.builder().monthlyAmount(new BigDecimal("1000000")).build()));
        when(payslipRepository.save(any(Payslip.class))).thenAnswer(invocation -> {
            Payslip saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        stubNoDeductions();

        service().calculate(1L, new PayrollActionRequest(end));

        ArgumentCaptor<Payslip> captor = ArgumentCaptor.forClass(Payslip.class);
        verify(payslipRepository).save(captor.capture());
        assertEquals(new BigDecimal("769230.77"), captor.getValue().getBaseSalaryPay());
        assertEquals(new BigDecimal("57692.31"), captor.getValue().getPositionAllowancePay());
        assertEquals(new BigDecimal("20000000"), captor.getValue().getContractualBaseSalary());
    }

    private PayrollServiceImpl service() {
        return new PayrollServiceImpl(
            payrollPeriodRepository,
            payslipRepository,
            payslipItemRepository,
            employeeRepository,
            employeeAssignmentRepository,
            employeeSalaryHistoryRepository,
            positionAllowanceRuleRepository,
            seniorityAllowanceRuleRepository,
            attendanceRecordRepository,
            accountRepository,
            currentAccountProvider,
            employeeAccessScopeService,
            attendanceCalendarService,
            payrollDeductionsService
        );
    }

    @Test
    void paymentMonthChangeCannotSilentlyReuseApprovedTax() {
        PayrollPeriod period = PayrollPeriod.builder().id(1L)
            .taxPaymentDate(LocalDate.of(2026, 11, 5))
            .status(PayrollPeriodStatus.APPROVED).build();
        when(payrollPeriodRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(period));

        assertThrows(ConflictException.class, () -> service().markPaid(1L,
            new PayrollActionRequest(LocalDate.of(2026, 12, 1))));
        assertEquals(PayrollPeriodStatus.APPROVED, period.getStatus());
    }

    private void stubNoDeductions() {
        when(payrollDeductionsService.calculate(anyLong(), any(LocalDate.class), any(LocalDate.class),
            any(BigDecimal.class), anyLong())).thenReturn(
                new PayrollDeductionsService.Deduction(1L, 1L, 1L, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
