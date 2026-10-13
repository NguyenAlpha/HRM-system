package com.htttdn.hrm.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Service
@Transactional
public class LeaveRequestService {

    private static final String REQUEST_READ = "request.read";
    private static final String REQUEST_MANAGE = "request.manage";
    private static final String SELF_RECORDED_COMMENT =
        "Tự ghi nhận: đơn nghỉ của Giám đốc hoặc Chủ sở hữu không cần duyệt";

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceService attendanceService;
    private final LeaveEntitlementService leaveEntitlementService;
    private final ApproverResolver approverResolver;

    public LeaveRequestService(
        LeaveRequestRepository leaveRequestRepository,
        EmployeeRepository employeeRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService,
        AttendanceService attendanceService,
        LeaveEntitlementService leaveEntitlementService,
        ApproverResolver approverResolver
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.attendanceService = attendanceService;
        this.leaveEntitlementService = leaveEntitlementService;
        this.approverResolver = approverResolver;
    }

    @PreAuthorize("hasAnyAuthority('request.self.create', 'request.manage')")
    public LeaveRequestView createDraft(CreateLeaveCommand command) {
        validateCommand(command);
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(command.employeeId())
            .orElseThrow(() -> employeeNotFound(command.employeeId()));
        requireSelfOrScopedAccess(command.employeeId(), REQUEST_MANAGE);

        Instant now = Instant.now();
        LeaveRequest request = LeaveRequest.builder()
            .employee(employee)
            .leaveType(command.leaveType())
            .salaryTreatment(resolveSalaryTreatment(command))
            .startAt(command.startAt())
            .endAt(command.endAt())
            .requestedMinutes(command.requestedMinutes())
            .reason(command.reason().trim())
            .attachmentUrl(normalizeNullable(command.attachmentUrl()))
            .status(LeaveRequestStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .build();
        return toView(leaveRequestRepository.save(request));
    }

    /**
     * Sends a draft to its approver: the direct manager, else the Director, else the Company Owner.
     * Leave of the Director or Company Owner needs no approval and is recorded as approved at once.
     */
    @PreAuthorize("hasAnyAuthority('request.self.create', 'request.manage')")
    public LeaveRequestView submit(Long requestId) {
        LeaveRequest request = findForUpdate(requestId);
        requireSelfOrScopedAccess(request.getEmployee().getId(), REQUEST_MANAGE);
        requireStatus(request, LeaveRequestStatus.DRAFT);

        Instant now = Instant.now();
        request.setSubmittedAt(now);
        if (approverResolver.isTopManagement(request.getEmployee())) {
            applyApproval(request, SELF_RECORDED_COMMENT);
            return toView(request);
        }
        Employee approver = approverResolver.approverFor(request.getEmployee())
            .orElseThrow(() -> new ConflictException(
                ErrorCode.APPROVER_NOT_FOUND,
                "Nobody can approve this leave: set a manager, or appoint a Director or Company Owner"
            ));
        request.setApproverEmployee(approver);
        request.setStatus(LeaveRequestStatus.PENDING);
        request.setUpdatedAt(now);
        return toView(request);
    }

    @PreAuthorize("hasAnyAuthority('request.self.cancel', 'request.manage')")
    public LeaveRequestView cancel(Long requestId) {
        LeaveRequest request = findForUpdate(requestId);
        requireSelfOrScopedAccess(request.getEmployee().getId(), REQUEST_MANAGE);
        if (request.getStatus() != LeaveRequestStatus.DRAFT
            && request.getStatus() != LeaveRequestStatus.PENDING) {
            throw alreadyProcessed("Only DRAFT or PENDING leave requests can be cancelled");
        }
        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setUpdatedAt(Instant.now());
        return toView(request);
    }

    /** Only the approver fixed at submission decides; nobody is ever the approver of their own leave. */
    @PreAuthorize("isAuthenticated()")
    public LeaveRequestView approve(Long requestId, String reviewComment) {
        LeaveRequest request = findForUpdate(requestId);
        requireApprover(request);
        requireStatus(request, LeaveRequestStatus.PENDING);
        applyApproval(request, reviewComment);
        return toView(request);
    }

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

        leaveEntitlementService.requireSufficientBalance(request);
        attendanceService.applyApprovedLeave(request);
        review(request, LeaveRequestStatus.APPROVED, reviewComment);
    }

    @PreAuthorize("isAuthenticated()")
    public LeaveRequestView reject(Long requestId, String reviewComment) {
        LeaveRequest request = findForUpdate(requestId);
        requireApprover(request);
        requireStatus(request, LeaveRequestStatus.PENDING);
        review(request, LeaveRequestStatus.REJECTED, reviewComment);
        return toView(request);
    }

    @PreAuthorize("hasAnyAuthority('request.self.read', 'request.read')")
    @Transactional(readOnly = true)
    public LeaveRequestView getById(Long requestId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> requestNotFound(requestId));
        if (!isApprover(request)) {
            requireSelfOrScopedAccess(request.getEmployee().getId(), REQUEST_READ);
        }
        return toView(request);
    }

    @PreAuthorize("hasAnyAuthority('request.self.read', 'request.read')")
    @Transactional(readOnly = true)
    public Page<LeaveRequestView> listByEmployee(Long employeeId, Pageable pageable) {
        employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
            .orElseThrow(() -> employeeNotFound(employeeId));
        requireSelfOrScopedAccess(employeeId, REQUEST_READ);
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable)
            .map(this::toView);
    }

    /** The pending requests waiting for the signed-in employee's decision. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Page<LeaveRequestView> listPending(Pageable pageable) {
        Employee self = findCurrentAccount().getEmployee();
        if (self == null) {
            return Page.empty(pageable);
        }
        return leaveRequestRepository.findByApproverEmployeeIdAndStatusOrderByCreatedAtDesc(
            self.getId(), LeaveRequestStatus.PENDING, pageable).map(this::toView);
    }

    private void validateCommand(CreateLeaveCommand command) {
        if (!command.endAt().isAfter(command.startAt())) {
            throw validation("endAt must be after startAt", "endAt");
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

    private void review(
        LeaveRequest request,
        LeaveRequestStatus status,
        String reviewComment
    ) {
        Instant now = Instant.now();
        request.setStatus(status);
        request.setReviewedByAccount(findCurrentAccount());
        request.setReviewComment(normalizeNullable(reviewComment));
        request.setReviewedAt(now);
        request.setUpdatedAt(now);
    }

    private boolean isApprover(LeaveRequest request) {
        Employee self = findCurrentAccount().getEmployee();
        return self != null && request.getApproverEmployee() != null
            && request.getApproverEmployee().getId().equals(self.getId());
    }

    private void requireApprover(LeaveRequest request) {
        if (!isApprover(request)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Only the approver of this leave request can decide it");
        }
    }

    private void requireSelfOrScopedAccess(Long employeeId, String scopedPermission) {
        Account actor = findCurrentAccount();
        if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
            return;
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, scopedPermission);
    }

    private LeaveRequest findForUpdate(Long requestId) {
        return leaveRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> requestNotFound(requestId));
    }

    private void requireStatus(LeaveRequest request, LeaveRequestStatus expected) {
        if (request.getStatus() != expected) {
            throw alreadyProcessed(
                "Leave request must be " + expected + " but was " + request.getStatus()
            );
        }
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Account not found: " + accountId
            ));
    }

    private BusinessException validation(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, field);
    }

    private ConflictException alreadyProcessed(String message) {
        return new ConflictException(ErrorCode.REQUEST_ALREADY_PROCESSED, message);
    }

    private ResourceNotFoundException employeeNotFound(Long employeeId) {
        return new ResourceNotFoundException(
            ErrorCode.EMPLOYEE_NOT_FOUND,
            "Employee not found: " + employeeId
        );
    }

    private ResourceNotFoundException requestNotFound(Long requestId) {
        return new ResourceNotFoundException(
            ErrorCode.REQUEST_NOT_FOUND,
            "Leave request not found: " + requestId
        );
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LeaveRequestView toView(LeaveRequest request) {
        return new LeaveRequestView(
            request.getId(),
            request.getEmployee().getId(),
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

    public record CreateLeaveCommand(
        @NotNull Long employeeId,
        @NotNull LeaveType leaveType,
        @Nullable LeaveSalaryTreatment salaryTreatment,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotNull @Positive Integer requestedMinutes,
        @NotBlank String reason,
        @Nullable String attachmentUrl
    ) {
    }

    public record LeaveRequestView(
        Long id,
        Long employeeId,
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
