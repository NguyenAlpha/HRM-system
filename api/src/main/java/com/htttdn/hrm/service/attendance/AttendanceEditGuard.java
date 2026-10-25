package com.htttdn.hrm.service.attendance;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ngày công thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa thì không sửa được nữa (409
 * {@code PAYROLL_PERIOD_LOCKED}). Dùng chung cho {@link AttendanceService} và {@link OvertimeService}.
 *
 * <p>Là {@code @Component} không có {@code @Transactional}: chạy trong transaction của service gọi nó.
 */
@Component
@RequiredArgsConstructor
public class AttendanceEditGuard {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;

    public void requireEditable(LocalDate workDate) {
        payrollPeriodRepository.findContainingDateForUpdate(workDate)
            .filter(period -> period.getStatus() == PayrollPeriodStatus.APPROVED
                || period.getStatus() == PayrollPeriodStatus.PAID
                || period.getStatus() == PayrollPeriodStatus.LOCKED)
            .ifPresent(period -> {
                throw new ConflictException(ErrorCode.PAYROLL_PERIOD_LOCKED,
                    "Attendance belongs to approved payroll period " + period.getId());
            });
    }

    /** Ngày công theo id, đã kiểm tra còn sửa được, kèm khóa ghi. */
    public AttendanceRecord getEditableForUpdate(Long id) {
        AttendanceRecord existing = attendanceRecordRepository.getRequired(id);
        requireEditable(existing.getWorkDate());
        return attendanceRecordRepository.getRequiredForUpdate(id);
    }
}
