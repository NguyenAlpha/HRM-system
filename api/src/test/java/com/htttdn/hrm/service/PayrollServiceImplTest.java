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
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
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
import com.htttdn.hrm.service.impl.PayrollServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void calculateUsesNewSalaryAndAllowancePoliciesAndApprovedOvertimeOnly() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
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
            .workDate(LocalDate.of(2026, 1, 15))
            .build();

        when(payrollPeriodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(accountRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(employeeRepository.findByEmploymentStatusInAndDeletedAtIsNull(any()))
            .thenReturn(List.of(employee));
        when(employeeSalaryHistoryRepository.findEffective(2L, end)).thenReturn(Optional.of(salary));
        when(employeeAssignmentRepository.findCurrentPrimaryCandidates(2L, end))
            .thenReturn(List.of(assignment));
        when(attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(2L, start, end))
            .thenReturn(List.of(attendance));
        when(positionAllowanceRuleRepository.findEffective(3L, end)).thenReturn(Optional.of(
            PositionAllowanceRule.builder().monthlyAmount(new BigDecimal("1000000")).build()
        ));
        when(seniorityAllowanceRuleRepository.findEffective(6, end)).thenReturn(Optional.of(
            SeniorityAllowanceRule.builder().percentage(new BigDecimal("5")).build()
        ));
        when(payslipRepository.findByPayrollPeriodIdAndEmployeeId(1L, 2L))
            .thenReturn(Optional.empty());
        when(payslipRepository.save(any(Payslip.class))).thenAnswer(invocation -> {
            Payslip saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        service().calculate(1L, new PayrollActionRequest(9L));

        ArgumentCaptor<Payslip> payslipCaptor = ArgumentCaptor.forClass(Payslip.class);
        verify(payslipRepository).save(payslipCaptor.capture());
        Payslip payslip = payslipCaptor.getValue();
        assertEquals(new BigDecimal("10000000.00"), payslip.getBaseSalaryPay());
        assertEquals(new BigDecimal("1000000"), payslip.getPositionAllowancePay());
        assertEquals(new BigDecimal("500000.00"), payslip.getSeniorityAllowancePay());
        assertEquals(new BigDecimal("1875000.00"), payslip.getOvertimePay());
        assertEquals(new BigDecimal("13375000.00"), payslip.getGrossPay());
        assertEquals(60, payslip.getApprovedOvertimeMinutes());
        verify(payslipItemRepository).saveAll(any());
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
            accountRepository
        );
    }
}
