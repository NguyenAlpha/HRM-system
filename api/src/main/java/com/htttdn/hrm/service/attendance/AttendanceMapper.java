package com.htttdn.hrm.service.attendance;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.entity.AttendanceRecord;

/** Ngày công ra response; dùng chung cho các service chấm công. */
@Component
public class AttendanceMapper {

    public AttendanceRecordResponse toResponse(AttendanceRecord record) {
        return new AttendanceRecordResponse(
            record.getId(),
            record.getEmployee().getId(),
            record.getWorkDate(),
            record.getShift().getId(),
            record.getScheduledStartAt(),
            record.getScheduledEndAt(),
            record.getScheduledMinutes(),
            record.getCheckInAt(),
            record.getCheckOutAt(),
            record.getWorkedMinutes(),
            record.getPayableMinutes(),
            record.getLateMinutes(),
            record.getEarlyLeaveMinutes(),
            record.getOvertimeMinutes(),
            record.getOvertimeMultiplier(),
            record.isOvertimeTaxExempt(),
            record.getStatus()
        );
    }
}
