package com.htttdn.hrm.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.attendance.AdjustAttendanceRequest;
import com.htttdn.hrm.dto.request.attendance.ApproveOvertimeRequest;
import com.htttdn.hrm.dto.request.attendance.CheckInRequest;
import com.htttdn.hrm.dto.request.attendance.CheckOutRequest;
import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;

public interface AttendanceService {

    AttendanceRecordResponse checkIn(CheckInRequest request);

    AttendanceRecordResponse checkOut(CheckOutRequest request);

    AttendanceRecordResponse approveOvertime(Long attendanceId, ApproveOvertimeRequest request);

    AttendanceRecordResponse adjust(Long attendanceId, AdjustAttendanceRequest request);

    AttendanceRecordResponse getById(Long id);

    Page<AttendanceRecordResponse> listByEmployee(Long employeeId, LocalDate from, LocalDate to, Pageable pageable);
}
