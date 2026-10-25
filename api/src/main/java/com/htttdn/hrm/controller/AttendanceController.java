package com.htttdn.hrm.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.request.attendance.RecordPunchesRequest;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.attendance.OvertimeApprovalItemResponse;
import com.htttdn.hrm.dto.response.attendance.TimesheetRowResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.service.attendance.AttendanceQueryService;
import com.htttdn.hrm.service.attendance.AttendanceService;
import com.htttdn.hrm.service.attendance.OvertimeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/attendance")
@Validated
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService service;
    private final OvertimeService overtimeService;
    private final AttendanceQueryService queryService;

    @PostMapping("/prepare")
    @PreAuthorize("hasAuthority('attendance.manage')")
    public ApiResult<Integer> prepare(@RequestParam @Min(2000) @Max(9999) int year,
        @RequestParam @Min(1) @Max(12) int month) {
        return ApiResult.ok(service.prepareMonth(year, month));
    }

    @PostMapping("/punches")
    @PreAuthorize("hasAuthority('attendance.manage')")
    public ApiResult<AttendanceRecordResponse> recordPunches(@Valid @RequestBody RecordPunchesRequest request) {
        return ApiResult.ok(service.recordPunches(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('attendance.manage')")
    public ApiResult<AttendanceRecordResponse> adjust(@PathVariable Long id,
        @Valid @RequestBody AdjustAttendanceRequest request) {
        return ApiResult.ok(service.adjust(id, request));
    }

    @PostMapping("/{id}/overtime-approval")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<AttendanceRecordResponse> approveOvertime(@PathVariable Long id,
        @Valid @RequestBody ApproveOvertimeRequest request) {
        return ApiResult.ok(overtimeService.approveOvertime(id, request));
    }

    @GetMapping("/timesheet")
    @PreAuthorize("hasAuthority('attendance.read')")
    public ApiResult<List<TimesheetRowResponse>> timesheet(@RequestParam @Min(2000) @Max(9999) int year,
        @RequestParam @Min(1) @Max(12) int month,
        @RequestParam(required = false) String keyword, @RequestParam(required = false) Long organizationUnitId) {
        return ApiResult.ok(queryService.timesheet(year, month, keyword, organizationUnitId));
    }

    @GetMapping("/overtime/pending")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<OvertimeApprovalItemResponse>> listOvertimeAwaitingDecision() {
        return ApiResult.ok(overtimeService.listOvertimeAwaitingDecision());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    public ApiResult<AttendanceRecordResponse> getById(@PathVariable Long id) {
        return ApiResult.ok(queryService.getById(id));
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    public ApiResult<PagedResult<AttendanceRecordResponse>> listByEmployee(@PathVariable Long employeeId,
        @RequestParam LocalDate from, @RequestParam LocalDate to,
        @PageableDefault(sort = "workDate", size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(queryService.listByEmployee(employeeId, from, to, pageable)));
    }
}
