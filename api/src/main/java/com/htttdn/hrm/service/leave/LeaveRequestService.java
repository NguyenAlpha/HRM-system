package com.htttdn.hrm.service.leave;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.service.attendance.LeaveAttendanceService;
import com.htttdn.hrm.service.NotificationService;
import com.htttdn.hrm.service.employee.ApproverResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.RequiredArgsConstructor;

/**
 * Vòng đời đơn nghỉ phép: nhân viên tạo nháp → gửi duyệt ({@code PENDING}) → người duyệt duyệt
 * ({@code APPROVED}) hoặc từ chối ({@code REJECTED}); nhân viên hủy được khi còn nháp hoặc chờ duyệt
 * ({@code CANCELLED}). Phần chỉ đọc nằm ở {@link LeaveRequestQueryService}.
 *
 * <p>Duyệt đơn ghi ngày nghỉ vào chấm công ({@code LeaveAttendanceService.applyApprovedLeave}) và trừ vào quỹ phép
 * năm ({@link LeaveEntitlementService}); người duyệt được chọn lúc gửi ({@code ApproverResolver}) và nhận
 * thông báo ({@code NotificationService}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final String REQUEST_MANAGE = "request.manage";
    private static final String SELF_RECORDED_COMMENT =
        "Tự ghi nhận: đơn nghỉ của Giám đốc hoặc Chủ sở hữu không cần duyệt";

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final LeaveAttendanceService leaveAttendanceService;
    private final LeaveEntitlementService leaveEntitlementService;
    private final ApproverResolver approverResolver;
    private final NotificationService notificationService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final Clock clock;

    /**
     * Nhân viên tạo đơn nghỉ nháp cho chính mình.
     *
     * <p>The minutes are worked out from the employee's shifts over the period, never taken from the
     * client, so the period and the time drawn from the annual balance cannot disagree.
     *
     * @throws BusinessException kết thúc không sau bắt đầu, chọn cách trả lương khác mặc định mà thiếu
     *         {@code request.manage} (→ 400 {@code VALIDATION_ERROR}), hoặc khoảng nghỉ không có giờ làm
     *         theo lịch (→ 400 {@code LEAVE_OUTSIDE_WORK_SCHEDULE}), hoặc có ngày trước ngày vào làm hay sau ngày
     *         làm việc cuối (→ 400 {@code LEAVE_OUTSIDE_EMPLOYMENT})
     * @throws com.htttdn.hrm.exception.ForbiddenException tạo đơn cho người khác (→ 403)
     * @throws ConflictException nghỉ phép năm vượt quỹ còn lại (→ 409 {@code INSUFFICIENT_LEAVE_BALANCE})
     */
    @PreAuthorize("hasAuthority('request.self.create')")
    public LeaveRequestView createDraft(CreateLeaveCommand command) {
        validateCommand(command);
        requireOwnEmployee(command.employeeId());
        Employee employee = employeeRepository.getRequired(command.employeeId());
        requireWithinEmployment(employee, command.startAt(), command.endAt());
        LeaveSalaryTreatment salaryTreatment = resolveSalaryTreatment(command);
        int requestedMinutes = leaveAttendanceService.leaveMinutesFor(employee.getId(), command.startAt(), command.endAt());
        if (requestedMinutes == 0) {
            throw new BusinessException(ErrorCode.LEAVE_OUTSIDE_WORK_SCHEDULE,
                "The leave period contains no scheduled work time", "endAt");
        }
        LeaveRequest request = LeaveRequest.builder()
            .employee(employee)
            .leaveType(command.leaveType())
            .salaryTreatment(salaryTreatment)
            .startAt(command.startAt())
            .endAt(command.endAt())
            .requestedMinutes(requestedMinutes)
            .reason(command.reason().trim())
            .attachmentUrl(Strings.trimToNull(command.attachmentUrl()))
            .status(LeaveRequestStatus.DRAFT)
            .build();
        leaveEntitlementService.requireSufficientBalance(request);
        return leaveRequestMapper.toView(leaveRequestRepository.save(request));
    }

    /**
     * Sends a draft to its approver: the direct manager, else the Director, else the Company Owner.
     * Leave of the Director or Company Owner needs no approval and is recorded as approved at once.
     *
     * @throws com.htttdn.hrm.exception.ForbiddenException gửi đơn của người khác (→ 403)
     * @throws ConflictException đơn không còn nháp (→ 409 {@code REQUEST_ALREADY_PROCESSED}), quỹ phép không
     *         đủ (→ 409 {@code INSUFFICIENT_LEAVE_BALANCE}), hoặc không ai duyệt được (→ 409
     *         {@code APPROVER_NOT_FOUND})
     */
    @PreAuthorize("hasAuthority('request.self.create')")
    public LeaveRequestView submit(Long requestId) {
        LeaveRequest request = leaveRequestRepository.getRequiredForUpdate(requestId);
        requireOwnEmployee(request.getEmployee().getId());
        requireStatus(request, LeaveRequestStatus.DRAFT);
        // Other requests may have been submitted or approved since this draft was created.
        leaveEntitlementService.requireSufficientBalance(request);

        Instant now = Instant.now(clock);
        request.setSubmittedAt(now);
        if (approverResolver.isTopManagement(request.getEmployee())) {
            applyApproval(request, SELF_RECORDED_COMMENT);
            return leaveRequestMapper.toView(leaveRequestRepository.saveAndFlush(request));
        }
        Employee approver = approverResolver.approverFor(request.getEmployee())
            .orElseThrow(() -> new ConflictException(
                ErrorCode.APPROVER_NOT_FOUND,
                "Nobody can approve this leave: set a manager, or appoint a Director or Company Owner"
            ));
        request.setApproverEmployee(approver);
        request.setStatus(LeaveRequestStatus.PENDING);
        notificationService.notifyLeaveSubmitted(request);
        return leaveRequestMapper.toView(leaveRequestRepository.saveAndFlush(request));
    }

    /**
     * Nhân viên rút đơn của mình khi còn nháp hoặc đang chờ duyệt.
     *
     * @throws com.htttdn.hrm.exception.ForbiddenException rút đơn của người khác (→ 403)
     * @throws ConflictException đơn đã duyệt/từ chối/hủy (→ 409 {@code REQUEST_ALREADY_PROCESSED})
     */
    @PreAuthorize("hasAuthority('request.self.cancel')")
    public LeaveRequestView cancel(Long requestId) {
        LeaveRequest request = leaveRequestRepository.getRequiredForUpdate(requestId);
        requireOwnEmployee(request.getEmployee().getId());
        if (request.getStatus() != LeaveRequestStatus.DRAFT
            && request.getStatus() != LeaveRequestStatus.PENDING) {
            throw alreadyProcessed("Only DRAFT or PENDING leave requests can be cancelled");
        }
        request.setStatus(LeaveRequestStatus.CANCELLED);
        return leaveRequestMapper.toView(leaveRequestRepository.saveAndFlush(request));
    }

    /**
     * Only the approver fixed at submission decides; nobody is ever the approver of their own leave.
     *
     * <p>Duyệt thì ghi ngày nghỉ vào chấm công ({@link #applyApproval}) rồi báo cho nhân viên.
     *
     * @throws com.htttdn.hrm.exception.ForbiddenException không phải người duyệt của đơn (→ 403)
     * @throws ConflictException đơn không còn chờ duyệt (→ 409 {@code REQUEST_ALREADY_PROCESSED}), hoặc lỗi
     *         khi áp vào chấm công/quỹ phép (xem {@link #applyApproval})
     */
    @PreAuthorize("isAuthenticated()")
    public LeaveRequestView approve(Long requestId, String reviewComment) {
        LeaveRequest request = leaveRequestRepository.getRequiredForUpdate(requestId);
        requireApprover(request);
        requireStatus(request, LeaveRequestStatus.PENDING);
        applyApproval(request, reviewComment);
        notificationService.notifyLeaveDecision(request);
        return leaveRequestMapper.toView(leaveRequestRepository.saveAndFlush(request));
    }

    /**
     * Chốt đơn là đã duyệt: dùng cho cả người duyệt bấm duyệt lẫn đơn tự ghi nhận của lãnh đạo.
     *
     * <p>Thứ tự: không trùng đơn đã duyệt khác → vẫn nằm trong thời gian làm việc → còn đủ quỹ phép → ghi vào
     * chấm công ({@code LeaveAttendanceService.applyApprovedLeave}) → ghi người duyệt, thời điểm. Lỗi ở bước nào
     * thì cả transaction rollback, đơn giữ trạng thái cũ.
     *
     * @throws ConflictException trùng đơn đã duyệt (→ 409 {@code CONFLICT}), quỹ phép không đủ (→ 409
     *         {@code INSUFFICIENT_LEAVE_BALANCE}), hoặc chấm công không nhận được ngày nghỉ, VD kỳ lương đã
     *         chốt (→ 409)
     * @throws BusinessException có ngày sau ngày làm việc cuối, VD HR hẹn nghỉ việc sau khi tạo đơn (→ 400
     *         {@code LEAVE_OUTSIDE_EMPLOYMENT})
     */
    private void applyApproval(LeaveRequest request, String reviewComment) {
        if (leaveRequestRepository.existsApprovedOverlap(
            request.getEmployee().getId(),
            request.getStartAt(),
            request.getEndAt(),
            request.getId()
        )) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Employee already has an approved leave request in this period"
            );
        }
        // The last working day may have been set after the request was created.
        requireWithinEmployment(request.getEmployee(), request.getStartAt(), request.getEndAt());

        leaveEntitlementService.requireSufficientBalance(request);
        leaveAttendanceService.applyApprovedLeave(request);
        review(request, LeaveRequestStatus.APPROVED, reviewComment);
    }

    /**
     * Người duyệt từ chối đơn đang chờ; chấm công và quỹ phép không đổi.
     *
     * @throws com.htttdn.hrm.exception.ForbiddenException không phải người duyệt của đơn (→ 403)
     * @throws ConflictException đơn không còn chờ duyệt (→ 409 {@code REQUEST_ALREADY_PROCESSED})
     */
    @PreAuthorize("isAuthenticated()")
    public LeaveRequestView reject(Long requestId, String reviewComment) {
        LeaveRequest request = leaveRequestRepository.getRequiredForUpdate(requestId);
        requireApprover(request);
        requireStatus(request, LeaveRequestStatus.PENDING);
        review(request, LeaveRequestStatus.REJECTED, reviewComment);
        notificationService.notifyLeaveDecision(request);
        return leaveRequestMapper.toView(leaveRequestRepository.saveAndFlush(request));
    }

    private void validateCommand(CreateLeaveCommand command) {
        if (!command.endAt().isAfter(command.startAt())) {
            throw validation("endAt must be after startAt", "endAt");
        }
    }

    /**
     * Mọi ngày của khoảng nghỉ phải nằm từ ngày vào làm tới ngày làm việc cuối (kể cả ngày cuối HR mới hẹn):
     * sau ngày cuối phân công chính vẫn còn mở tới khi job chốt, nên lịch ca không tự chặn được.
     */
    private void requireWithinEmployment(Employee employee, Instant startAt, Instant endAt) {
        LocalDate firstDay = LocalDate.ofInstant(startAt, ClockConfig.BUSINESS_ZONE);
        // endAt là mốc mở, như LeaveAttendanceService.leaveMinutesFor.
        LocalDate lastDay = LocalDate.ofInstant(endAt.minusNanos(1), ClockConfig.BUSINESS_ZONE);
        if (!employee.isEmployedThroughout(firstDay, lastDay)) {
            throw new BusinessException(ErrorCode.LEAVE_OUTSIDE_EMPLOYMENT,
                "Leave must fall between the hire date and the last working day", "endAt");
        }
    }

    /**
     * The payroll consequence of a leave request follows its type, not what the client sent.
     * Without this an employee could file SICK or OTHER leave marked EMPLOYER_PAID, be paid in
     * full, and never touch the annual quota, which only inspects ANNUAL.
     */
    private LeaveSalaryTreatment resolveSalaryTreatment(CreateLeaveCommand command) {
        LeaveSalaryTreatment derived = command.leaveType().defaultSalaryTreatment();
        if (command.salaryTreatment() == null || command.salaryTreatment() == derived) {
            return derived;
        }
        if (!currentAccountProvider.hasAuthority(REQUEST_MANAGE)) {
            throw validation(
                "Leave of type %s is treated as %s; only request.manage may choose another treatment"
                    .formatted(command.leaveType(), derived),
                "salaryTreatment"
            );
        }
        return command.salaryTreatment();
    }

    /** Ghi kết quả xét đơn: trạng thái, tài khoản người xét, ý kiến (rỗng → null) và thời điểm. */
    private void review(
        LeaveRequest request,
        LeaveRequestStatus status,
        String reviewComment
    ) {
        Instant now = Instant.now(clock);
        request.setStatus(status);
        request.setReviewedByAccount(currentAccountProvider.account());
        request.setReviewComment(Strings.trimToNull(reviewComment));
        request.setReviewedAt(now);
    }

    private void requireApprover(LeaveRequest request) {
        if (!request.isApprover(currentAccountProvider.account())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Only the approver of this leave request can decide it");
        }
    }

    private void requireOwnEmployee(Long employeeId) {
        if (!currentAccountProvider.account().belongsTo(employeeId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                "Only the employee can create, submit, or cancel their own leave request");
        }
    }

    private void requireStatus(LeaveRequest request, LeaveRequestStatus expected) {
        if (request.getStatus() != expected) {
            throw alreadyProcessed(
                "Leave request must be " + expected + " but was " + request.getStatus()
            );
        }
    }

    private BusinessException validation(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, field);
    }

    private ConflictException alreadyProcessed(String message) {
        return new ConflictException(ErrorCode.REQUEST_ALREADY_PROCESSED, message);
    }

    public record CreateLeaveCommand(
        @NotNull Long employeeId,
        @NotNull LeaveType leaveType,
        @Nullable LeaveSalaryTreatment salaryTreatment,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotBlank String reason,
        @Nullable String attachmentUrl
    ) {
    }

    public record LeaveRequestView(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LeaveType leaveType,
        LeaveSalaryTreatment salaryTreatment,
        Instant startAt,
        Instant endAt,
        Integer requestedMinutes,
        String reason,
        @Nullable String attachmentUrl,
        LeaveRequestStatus status,
        @Nullable Instant submittedAt,
        /** Who decides the request; null for drafts and for the Director's or Company Owner's own leave. */
        @Nullable Long approverEmployeeId,
        @Nullable String approverEmployeeName,
        @Nullable Long reviewedByAccountId,
        @Nullable String reviewComment,
        @Nullable Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
