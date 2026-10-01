package com.htttdn.hrm.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.report.HrWorkforceReportResponse;
import com.htttdn.hrm.dto.response.report.PayrollSalaryReportResponse;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.service.ReportService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
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
}
