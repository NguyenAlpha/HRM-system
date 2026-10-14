package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.report.PayrollSummaryReportResponse;
import com.htttdn.hrm.dto.response.report.PayrollSummaryReportResponse.Amounts;
import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.repository.PayslipRepository;

/** Báo cáo quỹ lương, bảo hiểm và thuế; dữ liệu toàn công ty như bảng lương nên cần scope COMPANY. */
@Service
@Transactional(readOnly = true)
public class PayrollReportService {

    private static final String PAYROLL_REPORT_READ = "report.payroll.read";

    private final PayslipRepository payslipRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public PayrollReportService(
        PayslipRepository payslipRepository,
        PayrollPeriodRepository payrollPeriodRepository,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.payslipRepository = payslipRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    @PreAuthorize("hasAuthority('report.payroll.read')")
    public PayrollSummaryReportResponse getYearSummary(int year) {
        employeeAccessScopeService.requireCompanyWide(PAYROLL_REPORT_READ);
        List<Payslip> payslips = payslipRepository.findForReportYear((short) year);
        Map<Integer, PayrollPeriod> periods = payrollPeriodRepository
            .findByYearAndStatusNot((short) year, PayrollPeriodStatus.CANCELLED).stream()
            .collect(Collectors.toMap(period -> period.getMonth().intValue(), Function.identity()));
        Map<Integer, List<Payslip>> byMonth = payslips.stream()
            .collect(Collectors.groupingBy(payslip -> payslip.getPayrollPeriod().getMonth().intValue()));

        List<PayrollSummaryReportResponse.Month> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            PayrollPeriod period = periods.get(month);
            months.add(new PayrollSummaryReportResponse.Month(month,
                period == null ? null : period.getId(),
                period == null ? null : period.getStatus(),
                sum(byMonth.getOrDefault(month, List.of()))));
        }

        Map<String, List<Payslip>> byUnit = payslips.stream().collect(Collectors.groupingBy(
            payslip -> Objects.requireNonNullElse(payslip.getOrganizationUnitSnapshot(), "Chưa có đơn vị"),
            TreeMap::new, Collectors.toList()));
        List<PayrollSummaryReportResponse.Unit> units = byUnit.entrySet().stream()
            .map(entry -> new PayrollSummaryReportResponse.Unit(entry.getKey(), sum(entry.getValue())))
            .sorted(Comparator.comparing((PayrollSummaryReportResponse.Unit unit) -> unit.amounts().grossPay()).reversed())
            .toList();

        return new PayrollSummaryReportResponse(year, sum(payslips), months, units);
    }

    private Amounts sum(List<Payslip> payslips) {
        return new Amounts(
            payslips.size(),
            total(payslips, Payslip::getGrossPay),
            total(payslips, Payslip::getAllowancePay),
            total(payslips, Payslip::getOvertimePay),
            total(payslips, Payslip::getEmployeeSocialInsurance),
            total(payslips, Payslip::getEmployeeHealthInsurance),
            total(payslips, Payslip::getEmployeeUnemploymentInsurance),
            total(payslips, Payslip::getPersonalIncomeTax),
            total(payslips, Payslip::getOtherDeductions),
            total(payslips, Payslip::getNetPay)
        );
    }

    private BigDecimal total(List<Payslip> payslips, Function<Payslip, BigDecimal> amount) {
        return payslips.stream().map(amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
