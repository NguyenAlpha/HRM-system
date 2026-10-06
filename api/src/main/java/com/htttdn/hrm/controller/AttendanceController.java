package com.htttdn.hrm.controller;

import java.time.LocalDate;

import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.request.attendance.CheckInRequest;
import com.htttdn.hrm.dto.request.attendance.CheckOutRequest;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.common.PagedResult;
import com.htttdn.hrm.service.AttendanceService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/attendance")
@Validated
public class AttendanceController {
    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('attendance.self.record', 'attendance.manage')")
    public ApiResult<AttendanceRecordResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ApiResult.ok(service.checkIn(request));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasAnyAuthority('attendance.self.record', 'attendance.manage')")
    public ApiResult<AttendanceRecordResponse> checkOut(@Valid @RequestBody CheckOutRequest request) {
        return ApiResult.ok(service.checkOut(request));
    }

    @PostMapping("/prepare")
    @PreAuthorize("hasAuthority('attendance.manage')")
    public ApiResult<Integer> prepare(@RequestParam @Min(2000) @Max(9999) int year,
        @RequestParam @Min(1) @Max(12) int month) {
        return ApiResult.ok(service.prepareMonth(year, month));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('attendance.manage')")
    public ApiResult<AttendanceRecordResponse> adjust(@PathVariable Long id,
        @Valid @RequestBody AdjustAttendanceRequest request) {
        return ApiResult.ok(service.adjust(id, request));
    }

    @PostMapping("/{id}/overtime-approval")
    @PreAuthorize("hasAuthority('attendance.overtime.approve')")
    public ApiResult<AttendanceRecordResponse> approveOvertime(@PathVariable Long id,
        @Valid @RequestBody ApproveOvertimeRequest request) {
        return ApiResult.ok(service.approveOvertime(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    public ApiResult<AttendanceRecordResponse> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    public ApiResult<PagedResult<AttendanceRecordResponse>> listByEmployee(@PathVariable Long employeeId,
        @RequestParam LocalDate from, @RequestParam LocalDate to,
        @PageableDefault(sort = "workDate", size = 20) Pageable pageable) {
        return ApiResult.ok(PagedResult.of(service.listByEmployee(employeeId, from, to, pageable)));
    }
}
