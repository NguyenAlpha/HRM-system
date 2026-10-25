package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.request.employee.AssignEmployeeRequest;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.EmploymentType;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.JobPositionRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAssignmentServiceTest {

    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private OrganizationUnitRepository organizationUnitRepository;
    @Mock private WorkLocationRepository workLocationRepository;
    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private WorkShiftRepository workShiftRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayslipItemRepository payslipItemRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @BeforeEach
    void setUp() {
        Employee employee = Employee.builder()
            .id(1L).hireDate(LocalDate.of(2024, 1, 1)).employmentStatus(EmploymentStatus.ACTIVE).build();
        when(employeeRepository.getRequired(1L)).thenReturn(employee);
        when(employeeRepository.getRequiredForUpdate(1L)).thenReturn(employee);
        when(organizationUnitRepository.findById(2L))
            .thenReturn(Optional.of(OrganizationUnit.builder().id(2L).isActive(true).build()));
        when(workLocationRepository.findById(4L))
            .thenReturn(Optional.of(WorkLocation.builder().id(4L).isActive(true).build()));
        when(jobPositionRepository.findById(3L))
            .thenReturn(Optional.of(JobPosition.builder().id(3L).isActive(true).build()));
    }

    @Test
    void assignInvalidatesCalculatedPeriodAndRemovesStalePayslip() {
        Account actor = Account.builder().id(9L).build();
        PayrollPeriod period = period(PayrollPeriodStatus.CALCULATED);
        Payslip payslip = Payslip.builder().id(11L).build();
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(eq(SEPTEMBER), any()))
            .thenReturn(List.of(period));
        when(payslipRepository.findByPayrollPeriodId(7L)).thenReturn(List.of(payslip));
        when(employeeAssignmentRepository.findOpenPrimaryForUpdate(1L)).thenReturn(List.of());
        when(currentAccountProvider.account()).thenReturn(actor);
        when(employeeAssignmentRepository.save(any(EmployeeAssignment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service().assign(1L, request());

        assertEquals(PayrollPeriodStatus.DRAFT, period.getStatus());
        verify(payslipItemRepository).deleteByPayslipId(11L);
        verify(payslipRepository).delete(payslip);
    }

    @Test
    void assignRejectsChangeAffectingApprovedPeriod() {
        when(payrollPeriodRepository.findAffectedPeriodsForUpdate(eq(SEPTEMBER), any()))
            .thenReturn(List.of(period(PayrollPeriodStatus.APPROVED)));

        assertThrows(ConflictException.class, () -> service().assign(1L, request()));
        verify(employeeAssignmentRepository, never()).save(any());
    }

    private static AssignEmployeeRequest request() {
        return new AssignEmployeeRequest(2L, 4L, 3L, null, null, EmploymentType.FULL_TIME, SEPTEMBER, null);
    }

    private static PayrollPeriod period(PayrollPeriodStatus status) {
        return PayrollPeriod.builder().id(7L).year((short) 2026).month((short) 9).status(status).build();
    }

    private EmployeeAssignmentService service() {
        return new EmployeeAssignmentService(
            employeeRepository,
            employeeAssignmentRepository,
            organizationUnitRepository,
            workLocationRepository,
            jobPositionRepository,
            workShiftRepository,
            payrollPeriodRepository,
            payslipRepository,
            payslipItemRepository,
            currentAccountProvider,
            employeeAccessScopeService,
            Clock.systemDefaultZone()
        );
    }
}
