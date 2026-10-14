package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.dto.response.report.PayrollSummaryReportResponse;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollReportServiceTest {

    @Mock private PayslipRepository payslipRepository;
    @Mock private PayrollPeriodRepository payrollPeriodRepository;
    @Mock private EmployeeAccessScopeService employeeAccessScopeService;

    @Test
    void sumsPayslipsByMonthAndByUnitWithEmptyMonthsKept() {
        PayrollPeriod january = period(11L, 1, PayrollPeriodStatus.LOCKED);
        PayrollPeriod february = period(12L, 2, PayrollPeriodStatus.DRAFT);
        when(payrollPeriodRepository.findByYearAndStatusNot((short) 2026, PayrollPeriodStatus.CANCELLED))
            .thenReturn(List.of(january, february));
        when(payslipRepository.findForReportYear((short) 2026)).thenReturn(List.of(
            payslip(january, "Kế toán", "10000000", "1050000", "500000", "8450000"),
            payslip(january, "Nhân sự", "20000000", "2100000", "1500000", "16400000"),
            payslip(january, "Nhân sự", "15000000", "1575000", "800000", "12625000")
        ));

        PayrollSummaryReportResponse report = service().getYearSummary(2026);

        verify(employeeAccessScopeService).requireCompanyWide("report.payroll.read");
        assertEquals(3, report.total().payslipCount());
        assertEquals(new BigDecimal("45000000"), report.total().grossPay());
        assertEquals(new BigDecimal("4725000"), report.total().socialInsurance());
        assertEquals(new BigDecimal("2800000"), report.total().personalIncomeTax());
        assertEquals(new BigDecimal("37475000"), report.total().netPay());

        assertEquals(12, report.months().size());
        assertEquals(11L, report.months().get(0).periodId());
        assertEquals(new BigDecimal("45000000"), report.months().get(0).amounts().grossPay());
        // February has a period but nothing calculated yet; March has no period at all.
        assertEquals(PayrollPeriodStatus.DRAFT, report.months().get(1).status());
        assertEquals(0, report.months().get(1).amounts().payslipCount());
        assertNull(report.months().get(2).periodId());

        // Largest gross first.
        assertEquals(List.of("Nhân sự", "Kế toán"), report.units().stream().map(PayrollSummaryReportResponse.Unit::organizationUnit).toList());
        assertEquals(2, report.units().getFirst().amounts().payslipCount());
    }

    private PayrollReportService service() {
        return new PayrollReportService(payslipRepository, payrollPeriodRepository, employeeAccessScopeService);
    }

    private static PayrollPeriod period(Long id, int month, PayrollPeriodStatus status) {
        return PayrollPeriod.builder().id(id).year((short) 2026).month((short) month).status(status).build();
    }

    /** All employee insurance is booked as social insurance; the sum per field is what is checked. */
    private static Payslip payslip(PayrollPeriod period, String unit, String gross, String insurance, String tax, String net) {
        return Payslip.builder()
            .payrollPeriod(period)
            .organizationUnitSnapshot(unit)
            .grossPay(new BigDecimal(gross))
            .allowancePay(BigDecimal.ZERO)
            .overtimePay(BigDecimal.ZERO)
            .employeeSocialInsurance(new BigDecimal(insurance))
            .employeeHealthInsurance(BigDecimal.ZERO)
            .employeeUnemploymentInsurance(BigDecimal.ZERO)
            .personalIncomeTax(new BigDecimal(tax))
            .otherDeductions(BigDecimal.ZERO)
            .netPay(new BigDecimal(net))
            .build();
    }
}
