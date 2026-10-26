package com.htttdn.hrm.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.htttdn.hrm.service.leave.LeaveEntitlementService;
import com.htttdn.hrm.service.leave.LeaveEntitlementService.EmployeeBalanceView;
import com.htttdn.hrm.service.payroll.PayrollReportService;
import com.htttdn.hrm.service.report.MonthlyHrStatusReportService;
import com.htttdn.hrm.service.report.SalaryReportService;
import com.htttdn.hrm.service.report.TurnoverReportService;
import com.htttdn.hrm.service.report.WorkforceReportService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final WorkforceReportService workforceReportService;
    private final SalaryReportService salaryReportService;
    private final TurnoverReportService turnoverReportService;
    private final MonthlyHrStatusReportService monthlyHrStatusReportService;
    private final LeaveEntitlementService leaveEntitlementService;
    private final PayrollReportService payrollReportService;

    @GetMapping("/hr/workforce-distribution")
    public ApiResult<HrWorkforceReportResponse> workforceDistribution(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
        @RequestParam(required = false) Long organizationUnitId,
        @RequestParam(required = false) Long workLocationId,
        @RequestParam(required = false) EmploymentStatus employmentStatus
    ) {
        return ApiResult.ok(workforceReportService.getWorkforceReport(
            asOfDate, organizationUnitId, workLocationId, employmentStatus
        ));
    }

    @GetMapping("/hr/turnover")
    public ApiResult<TurnoverReportResponse> turnover(@RequestParam @Min(2000) @Max(9999) int year) {
        return ApiResult.ok(turnoverReportService.getTurnoverReport(year));
    }

    @GetMapping("/hr/monthly-status")
    public ApiResult<MonthlyHrStatusReportResponse> monthlyStatus(
        @RequestParam @Min(2000) @Max(9999) int year,
        @RequestParam @Min(1) @Max(12) int month
    ) {
        return ApiResult.ok(monthlyHrStatusReportService.getMonthlyStatusReport(year, month));
    }

    @GetMapping("/hr/leave-balances")
    public ApiResult<List<EmployeeBalanceView>> leaveBalances(
        @RequestParam @Min(2000) @Max(9999) int year,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long organizationUnitId
    ) {
        return ApiResult.ok(leaveEntitlementService.balancesForReport(year, keyword, organizationUnitId));
    }

    @GetMapping("/payroll/salary-distribution")
    public ApiResult<PayrollSalaryReportResponse> salaryDistribution(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
        @RequestParam(required = false) Long organizationUnitId,
        @RequestParam(required = false) Long workLocationId,
        @RequestParam(required = false) EmploymentStatus employmentStatus
    ) {
        return ApiResult.ok(salaryReportService.getSalaryReport(
            asOfDate, organizationUnitId, workLocationId, employmentStatus
        ));
    }

    @GetMapping("/payroll/summary")
    public ApiResult<PayrollSummaryReportResponse> payrollSummary(@RequestParam @Min(2000) @Max(9999) int year) {
        return ApiResult.ok(payrollReportService.getYearSummary(year));
    }
}
