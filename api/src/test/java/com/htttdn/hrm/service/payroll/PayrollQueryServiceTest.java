package com.htttdn.hrm.service.payroll;

import java.util.List;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipItemRepository;
import com.htttdn.hrm.repository.PayslipRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollQueryServiceTest {

    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private PayslipRepository payslipRepository;
    @Mock private PayslipItemRepository payslipItemRepository;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @Test
    void employeeSeesTheirOwnPayslipOnceThePeriodIsApproved() {
        signInAsEmployee(2L);
        stubPayslip(2L, PayrollPeriodStatus.APPROVED);
        when(payslipItemRepository.findByPayslipId(5L)).thenReturn(List.of());

        assertEquals(5L, service().getPayslip(5L).id());
    }

    @Test
    void employeeCannotSeeTheirOwnPayslipBeforeApproval() {
        signInAsEmployee(2L);
        stubPayslip(2L, PayrollPeriodStatus.CALCULATED);

        assertThrows(ForbiddenException.class, () -> service().getPayslip(5L));
    }

    @Test
    void employeeCannotSeeAnotherEmployeesPayslip() {
        signInAsEmployee(2L);
        stubPayslip(3L, PayrollPeriodStatus.APPROVED);

        assertThrows(ForbiddenException.class, () -> service().getPayslip(5L));
    }

    @Test
    void payrollReaderSeesAPayslipBeforeApprovalWithinTheirScope() {
        when(currentAccountProvider.hasAuthority("payroll.self.read")).thenReturn(false);
        when(currentAccountProvider.hasAuthority("report.payroll.read")).thenReturn(true);
        stubPayslip(3L, PayrollPeriodStatus.CALCULATED);
        when(payslipItemRepository.findByPayslipId(5L)).thenReturn(List.of());

        assertEquals(5L, service().getPayslip(5L).id());
        verify(employeeAccessScopeService).requireEmployeeAccess(3L, "report.payroll.read");
    }

    private void signInAsEmployee(Long employeeId) {
        when(currentAccountProvider.hasAuthority("payroll.self.read")).thenReturn(true);
        when(currentAccountProvider.account())
            .thenReturn(Account.builder().id(9L).employee(Employee.builder().id(employeeId).build()).build());
    }

    private void stubPayslip(Long employeeId, PayrollPeriodStatus status) {
        Payslip payslip = Payslip.builder().id(5L)
            .employee(Employee.builder().id(employeeId).build())
            .payrollPeriod(PayrollPeriod.builder().id(1L).status(status).build())
            .build();
        when(payslipRepository.getRequired(5L)).thenReturn(payslip);
    }

    @Test
    void listsSortOnlyByKnownProperties() {
        PageRequest byHash = PageRequest.of(0, 20, Sort.by("passwordHash"));

        // An unknown property would otherwise fail inside the query and answer 500.
        BusinessException periods = assertThrows(BusinessException.class, () -> service().listPeriods(byHash));
        assertEquals("sort", periods.getField());
        BusinessException payslips = assertThrows(BusinessException.class,
            () -> service().listPayslipsForEmployee(2L, byHash));
        assertEquals("sort", payslips.getField());
    }

    private PayrollQueryService service() {
        return new PayrollQueryService(
            payrollPeriodRepository,
            payslipRepository,
            currentAccountProvider,
            employeeAccessScopeService,
            new PayrollMapper(payslipRepository, payslipItemRepository)
        );
    }
}
