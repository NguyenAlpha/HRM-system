package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.repository.specification.SearchPatterns;
import com.htttdn.hrm.security.CurrentAccountProvider;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final String REQUEST_READ = "request.read";
    private static final String REQUEST_MANAGE = "request.manage";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("startAt", "createdAt", "submittedAt", "status");
    private static final String SELF_RECORDED_COMMENT =
        "Tự ghi nhận: đơn nghỉ của Giám đốc hoặc Chủ sở hữu không cần duyệt";

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceService attendanceService;
    private final LeaveEntitlementService leaveEntitlementService;
    private final ApproverResolver approverResolver;
    private final NotificationService notificationService;

    /**
     * The minutes are worked out from the employee's shifts over the period, never taken from the
     * client, so the period and the time drawn from the annual balance cannot disagree.
     */
    @PreAuthorize("hasAuthority('request.self.create')")
    public LeaveRequestView createDraft(CreateLeaveCommand command) {
        validateCommand(command);
        requireOwnEmployee(command.employeeId());
        Employee employee = employeeRepository.getRequired(command.employeeId());
        LeaveSalaryTreatment salaryTreatment = resolveSalaryTreatment(command);
        int requestedMinutes = attendanceService.leaveMinutesFor(employee.getId(), command.startAt(), command.endAt());
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
            .attachmentUrl(normalizeNullable(command.attachmentUrl()))
            .status(LeaveRequestStatus.DRAFT)
            .build();
        leaveEntitlementService.requireSufficientBalance(request);
        return toView(leaveRequestRepository.save(request));
    }

    /**
     * Sends a draft to its approver: the direct manager, else the Director, else the Company Owner.
     * Leave of the Director or Company Owner needs no approval and is recorded as approved at once.
     */
    @PreAuthorize("hasAuthority('request.self.create')")
    public LeaveRequestView submit(Long requestId) {
        LeaveRequest request = findForUpdate(requestId);
        requireOwnEmployee(request.getEmployee().getId());
        requireStatus(request, LeaveRequestStatus.DRAFT);
        // Other requests may have been submitted or approved since this draft was created.
        leaveEntitlementService.requireSufficientBalance(request);

        Instant now = Instant.now();
        request.setSubmittedAt(now);
        if (approverResolver.isTopManagement(request.getEmployee())) {
            applyApproval(request, SELF_RECORDED_COMMENT);
            return toView(leaveRequestRepository.saveAndFlush(request));
        }
        Employee approver = approverResolver.approverFor(request.getEmployee())
            .orElseThrow(() -> new ConflictException(
                ErrorCode.APPROVER_NOT_FOUND,
                "Nobody can approve this leave: set a manager, or appoint a Director or Company Owner"
            ));
        request.setApproverEmployee(approver);
        request.setStatus(LeaveRequestStatus.PENDING);
        notificationService.notifyLeaveSubmitted(request);
        return toView(leaveRequestRepository.saveAndFlush(request));
    }

    @PreAuthorize("hasAuthority('request.self.cancel')")
    public LeaveRequestView cancel(Long requestId) {
        LeaveRequest request = findForUpdate(requestId);
        requireOwnEmployee(request.getEmployee().getId());
        if (request.getStatus() != LeaveRequestStatus.DRAFT
            && request.getStatus() != LeaveRequestStatus.PENDING) {
            throw alreadyProcessed("Only DRAFT or PENDING leave requests can be cancelled");
        }
        request.setStatus(LeaveRequestStatus.CANCELLED);
        return toView(leaveRequestRepository.saveAndFlush(request));
    }

    /** Only the approver fixed at submission decides; nobody is ever the approver of their own leave. */
    @PreAuthorize("isAuthenticated()")
    public LeaveRequestView approve(Long requestId, String reviewComment) {
        LeaveRequest request = findForUpdate(requestId);
        requireApprover(request);
        requireStatus(request, LeaveRequestStatus.PENDING);
        applyApproval(request, reviewComment);
        notificationService.notifyLeaveDecision(request);
        return toView(leaveRequestRepository.saveAndFlush(request));
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
        notificationService.notifyLeaveDecision(request);
        return toView(leaveRequestRepository.saveAndFlush(request));
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
        employeeRepository.getRequired(employeeId);
        requireSelfOrScopedAccess(employeeId, REQUEST_READ);
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable)
            .map(this::toView);
    }

    /**
     * Đơn nghỉ của mọi nhân viên trong scope {@code request.read}. Đơn nháp là của riêng người tạo
     * nên không bao giờ xuất hiện. {@code from}/{@code to} giữ các đơn có giao với khoảng ngày đó.
     */
    @PreAuthorize("hasAuthority('request.read')")
    @Transactional(readOnly = true)
    public Page<LeaveRequestView> search(LeaveRequestSearch search, Pageable pageable) {
        Specification<LeaveRequest> specification = (root, query, builder) ->
            builder.notEqual(root.get("status"), LeaveRequestStatus.DRAFT);

        boolean companyWide = employeeAccessScopeService.resolve(REQUEST_READ).companyWide();
        Specification<Employee> keyword = EmployeeSpecifications.matchesKeyword(search.keyword());
        Specification<Employee> unit = EmployeeSpecifications.currentlyAssignedTo(
            search.organizationUnitId(), null, LocalDate.now(BUSINESS_ZONE));
        if (!companyWide || keyword != null || unit != null) {
            // Scope and employee filters resolve to employee ids first; the scope rules are written for Employee roots.
            Specification<Employee> employees = employeeAccessScopeService.accessibleEmployees(REQUEST_READ);
            for (Specification<Employee> filter : Arrays.asList(keyword, unit)) {
                if (filter != null) {
                    employees = employees.and(filter);
                }
            }
            List<Long> employeeIds = employeeRepository.findAll(employees).stream().map(Employee::getId).toList();
            if (employeeIds.isEmpty()) {
                return Page.empty(pageable);
            }
            specification = specification.and((root, query, builder) -> root.get("employee").get("id").in(employeeIds));
        }
        if (search.status() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), search.status()));
        }
        if (search.leaveType() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("leaveType"), search.leaveType()));
        }
        if (search.from() != null) {
            Instant fromStart = search.from().atStartOfDay(BUSINESS_ZONE).toInstant();
            specification = specification.and((root, query, builder) -> builder.greaterThan(root.get("endAt"), fromStart));
        }
        if (search.to() != null) {
            Instant toEnd = search.to().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            specification = specification.and((root, query, builder) -> builder.lessThan(root.get("startAt"), toEnd));
        }
        return leaveRequestRepository.findAll(specification, SearchPatterns.requireSortable(pageable, SORTABLE_PROPERTIES))
            .map(this::toView);
    }

    /** The pending requests waiting for the signed-in employee's decision. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Page<LeaveRequestView> listPending(Pageable pageable) {
        Employee self = currentAccountProvider.account().getEmployee();
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
        request.setReviewedByAccount(currentAccountProvider.account());
        request.setReviewComment(normalizeNullable(reviewComment));
        request.setReviewedAt(now);
    }

    private boolean isApprover(LeaveRequest request) {
        Employee self = currentAccountProvider.account().getEmployee();
        return self != null && request.getApproverEmployee() != null
            && request.getApproverEmployee().getId().equals(self.getId());
    }

    private void requireApprover(LeaveRequest request) {
        if (!isApprover(request)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Only the approver of this leave request can decide it");
        }
    }

    private void requireSelfOrScopedAccess(Long employeeId, String scopedPermission) {
        Account actor = currentAccountProvider.account();
        if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
            return;
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, scopedPermission);
    }

    private void requireOwnEmployee(Long employeeId) {
        Employee self = currentAccountProvider.account().getEmployee();
        if (self == null || !employeeId.equals(self.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                "Only the employee can create, submit, or cancel their own leave request");
        }
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

    private BusinessException validation(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, field);
    }

    private ConflictException alreadyProcessed(String message) {
        return new ConflictException(ErrorCode.REQUEST_ALREADY_PROCESSED, message);
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

    /** Bộ lọc danh sách đơn nghỉ; trường null là không lọc. */
    public record LeaveRequestSearch(
        @Nullable String keyword,
        @Nullable Long organizationUnitId,
        @Nullable LeaveRequestStatus status,
        @Nullable LeaveType leaveType,
        @Nullable LocalDate from,
        @Nullable LocalDate to
    ) { }

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
