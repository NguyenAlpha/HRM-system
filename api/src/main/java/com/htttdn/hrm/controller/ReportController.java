package com.htttdn.hrm.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.report.HrWorkforceReportResponse;
import com.htttdn.hrm.dto.response.report.MonthlyHrStatusReportResponse;
import com.htttdn.hrm.dto.response.report.PayrollSalaryReportResponse;
import com.htttdn.hrm.dto.response.report.PayrollSummaryReportResponse;
import com.htttdn.hrm.dto.response.report.TurnoverReportResponse;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.service.LeaveEntitlementService;
import com.htttdn.hrm.service.LeaveEntitlementService.EmployeeBalanceView;
import com.htttdn.hrm.service.PayrollReportService;
import com.htttdn.hrm.service.ReportService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final LeaveEntitlementService leaveEntitlementService;
    private final PayrollReportService payrollReportService;

    public ReportController(
        ReportService reportService,
        LeaveEntitlementService leaveEntitlementService,
        PayrollReportService payrollReportService
    ) {
        this.reportService = reportService;
        this.leaveEntitlementService = leaveEntitlementService;
        this.payrollReportService = payrollReportService;
    }

    @GetMapping("/hr/workforce-distribution")
    @PreAuthorize("hasAuthority('report.hr.read')")
    public ApiResult<HrWorkforceReportResponse> workforceDistribution(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
        @RequestParam(required = false) Long organizationUnitId,
        @RequestParam(required = false) Long workLocationId,
        @RequestParam(required = false) EmploymentStatus employmentStatus
    ) {
        return ApiResult.ok(reportService.getWorkforceReport(
            asOfDate, organizationUnitId, workLocationId, employmentStatus
        ));
    }

    @GetMapping("/hr/turnover")
    @PreAuthorize("hasAuthority('report.hr.read')")
    public ApiResult<TurnoverReportResponse> turnover(@RequestParam @Min(2000) @Max(9999) int year) {
        return ApiResult.ok(reportService.getTurnoverReport(year));
    }

    @GetMapping("/hr/monthly-status")
    @PreAuthorize("hasAuthority('report.hr.read')")
    public ApiResult<MonthlyHrStatusReportResponse> monthlyStatus(
        @RequestParam @Min(2000) @Max(9999) int year,
        @RequestParam @Min(1) @Max(12) int month
    ) {
        return ApiResult.ok(reportService.getMonthlyStatusReport(year, month));
    }

    @GetMapping("/hr/leave-balances")
    @PreAuthorize("hasAuthority('report.hr.read')")
    public ApiResult<List<EmployeeBalanceView>> leaveBalances(
        @RequestParam @Min(2000) @Max(9999) int year,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long organizationUnitId
    ) {
        return ApiResult.ok(leaveEntitlementService.balancesForReport(year, keyword, organizationUnitId));
    }

    @GetMapping("/payroll/salary-distribution")
    @PreAuthorize("hasAuthority('report.payroll.read')")
    public ApiResult<PayrollSalaryReportResponse> salaryDistribution(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
        @RequestParam(required = false) Long organizationUnitId,
        @RequestParam(required = false) Long workLocationId,
        @RequestParam(required = false) EmploymentStatus employmentStatus
    ) {
        return ApiResult.ok(reportService.getSalaryReport(
            asOfDate, organizationUnitId, workLocationId, employmentStatus
        ));
    }

    @GetMapping("/payroll/summary")
    @PreAuthorize("hasAuthority('report.payroll.read')")
    public ApiResult<PayrollSummaryReportResponse> payrollSummary(@RequestParam @Min(2000) @Max(9999) int year) {
        return ApiResult.ok(payrollReportService.getYearSummary(year));
    }
}
