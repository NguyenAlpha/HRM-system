package com.htttdn.hrm.service.leave;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.service.leave.LeaveRequestService.LeaveRequestView;

/**
 * Đổi {@link LeaveRequest} thành {@link LeaveRequestView}, dùng chung cho phần ghi
 * ({@link LeaveRequestService}) và phần đọc ({@link LeaveRequestQueryService}).
 *
 * <p>Nhân viên, người duyệt và tài khoản người xét là quan hệ LAZY: đọc tên của họ chạy thêm query, nên
 * chỉ gọi trong transaction của service.
 */
@Component
public class LeaveRequestMapper {

    public LeaveRequestView toView(LeaveRequest request) {
        return new LeaveRequestView(
            request.getId(),
            request.getEmployee().getId(),
            request.getEmployee().getEmployeeCode(),
            request.getEmployee().getFullName(),
            request.getLeaveType(),
            request.getSalaryTreatment(),
            request.getStartAt(),
            request.getEndAt(),
            request.getRequestedMinutes(),
            request.getReason(),
            request.getAttachmentUrl(),
            request.getStatus(),
            request.getSubmittedAt(),
            request.getApproverEmployee() == null ? null : request.getApproverEmployee().getId(),
            request.getApproverEmployee() == null ? null : request.getApproverEmployee().getFullName(),
            request.getReviewedByAccount() == null ? null : request.getReviewedByAccount().getId(),
            request.getReviewComment(),
            request.getReviewedAt(),
            request.getCreatedAt(),
            request.getUpdatedAt()
        );
    }
}
