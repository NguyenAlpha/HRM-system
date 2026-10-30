package com.htttdn.hrm.service.employee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.employeerequest.CreateResignationRequestRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.ResignationRequest;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.ResignationRequestStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.ResignationRequestRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.repository.specification.SearchPatterns;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.util.Strings;

import jakarta.annotation.Nullable;

import lombok.RequiredArgsConstructor;

/**
 * Đơn nghỉ việc: nhân viên gửi, người duyệt theo tuyến quản lý (như đơn nghỉ) duyệt hoặc từ chối.
 * Đơn đã duyệt chưa kết thúc hợp đồng ngay: nhân viên vẫn làm đến hết ngày làm việc cuối, rồi
 * {@link ResignationCompletionJob} kết thúc hợp đồng ({@code RESIGNED}) bằng đúng logic HR dùng.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ResignationRequestService {

    private static final String REQUEST_READ = "request.read";
    private static final String REQUEST_MANAGE = "request.manage";
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "lastWorkingDate", "status");

    private final ResignationRequestRepository resignationRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final ApproverResolver approverResolver;
    private final EmploymentLifecycleService employmentLifecycleService;
    private final Clock clock;

    /** Sends the request to the employee's approver at once; one open request per employee. */
    @PreAuthorize("hasAnyAuthority('request.self.create', 'request.manage')")
    public ResignationRequestView create(CreateResignationRequestRequest command) {
        Employee employee = employeeRepository.getRequired(command.employeeId());
        requireSelfOrScopedAccess(employee.getId(), REQUEST_MANAGE);
        if (employee.getEmploymentStatus().isEnded()) {
            throw new ConflictException(ErrorCode.EMPLOYMENT_ALREADY_ENDED, "Employee employment has already ended");
        }
        requireLastWorkingDate(command.requestedLastWorkingDate(), employee);
        if (resignationRequestRepository.existsOpen(employee.getId())) {
            throw new ConflictException(ErrorCode.RESIGNATION_ALREADY_OPEN, "Employee already has an open resignation request");
        }
        Employee approver = approverResolver.approverFor(employee)
            .orElseThrow(() -> new ConflictException(
                ErrorCode.APPROVER_NOT_FOUND,
                "Nobody can approve this resignation: set a manager, or appoint a Director or Company Owner"
            ));
        return toView(resignationRequestRepository.save(ResignationRequest.builder()
            .employee(employee)
            .lastWorkingDate(command.requestedLastWorkingDate())
            .reason(command.reason().trim())
            .status(ResignationRequestStatus.PENDING)
            .approverEmployee(approver)
            .build()));
    }

    @PreAuthorize("hasAnyAuthority('request.self.cancel', 'request.manage')")
    public ResignationRequestView cancel(Long requestId) {
        ResignationRequest request = resignationRequestRepository.getRequiredForUpdate(requestId);
        requireSelfOrScopedAccess(request.getEmployee().getId(), REQUEST_MANAGE);
        requirePending(request);
        request.setStatus(ResignationRequestStatus.CANCELLED);
        return toView(request);
    }

    /**
     * Only the approver fixed at submission decides. {@code lastWorkingDate} replaces the requested
     * day, for example to respect the notice period; the day kept must not be in the past.
     */
    @PreAuthorize("isAuthenticated()")
    public ResignationRequestView approve(Long requestId, @Nullable String comment, @Nullable LocalDate lastWorkingDate) {
        ResignationRequest request = resignationRequestRepository.getRequiredForUpdate(requestId);
        requireApprover(request);
        requirePending(request);
        if (request.getEmployee().getEmploymentStatus().isEnded()) {
            throw new ConflictException(ErrorCode.EMPLOYMENT_ALREADY_ENDED, "Employee employment has already ended");
        }
        LocalDate effective = lastWorkingDate != null ? lastWorkingDate : request.getLastWorkingDate();
        requireLastWorkingDate(effective, request.getEmployee());
        request.setLastWorkingDate(effective);
        review(request, ResignationRequestStatus.APPROVED, comment);
        // Shown on the employee like an end HR schedules: nothing can then be dated after the last working day.
        employmentLifecycleService.scheduleEnd(request.getEmployee(), EmploymentStatus.RESIGNED, effective,
            request.getReason(), request.getReviewedByAccount());
        return toView(request);
    }

    @PreAuthorize("isAuthenticated()")
    public ResignationRequestView reject(Long requestId, String comment) {
        ResignationRequest request = resignationRequestRepository.getRequiredForUpdate(requestId);
        requireApprover(request);
        requirePending(request);
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A reason is required to reject", "comment");
        }
        review(request, ResignationRequestStatus.REJECTED, comment);
        return toView(request);
    }

    @PreAuthorize("hasAnyAuthority('request.self.read', 'request.read')")
    @Transactional(readOnly = true)
    public ResignationRequestView getById(Long requestId) {
        ResignationRequest request = resignationRequestRepository.getRequired(requestId);
        if (!isApprover(request)) {
            requireSelfOrScopedAccess(request.getEmployee().getId(), REQUEST_READ);
        }
        return toView(request);
    }

    @PreAuthorize("hasAnyAuthority('request.self.read', 'request.read')")
    @Transactional(readOnly = true)
    public Page<ResignationRequestView> listByEmployee(Long employeeId, Pageable pageable) {
        requireSelfOrScopedAccess(employeeId, REQUEST_READ);
        return resignationRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable).map(this::toView);
    }

    /** The pending requests waiting for the signed-in employee's decision. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Page<ResignationRequestView> listPending(Pageable pageable) {
        Employee self = currentAccountProvider.account().getEmployee();
        if (self == null) {
            return Page.empty(pageable);
        }
        return resignationRequestRepository.findByApproverEmployeeIdAndStatusOrderByCreatedAtDesc(
            self.getId(), ResignationRequestStatus.PENDING, pageable).map(this::toView);
    }

    /** Requests of every employee in the {@code request.read} scope. */
    @PreAuthorize("hasAuthority('request.read')")
    @Transactional(readOnly = true)
    public Page<ResignationRequestView> search(ResignationRequestSearch search, Pageable pageable) {
        Specification<ResignationRequest> specification = (root, query, builder) -> builder.conjunction();
        boolean companyWide = employeeAccessScopeService.resolve(REQUEST_READ).companyWide();
        Specification<Employee> keyword = EmployeeSpecifications.matchesKeyword(search.keyword());
        Specification<Employee> unit = EmployeeSpecifications.currentlyAssignedTo(
            search.organizationUnitId(), null, LocalDate.now(clock));
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
        return resignationRequestRepository.findAll(specification, SearchPatterns.requireSortable(pageable, SORTABLE_PROPERTIES))
            .map(this::toView);
    }

    /** Ids of approved requests whose last working day is before {@code today}. */
    @Transactional(readOnly = true)
    public List<Long> dueIds(LocalDate today) {
        return resignationRequestRepository.findDue(today).stream().map(ResignationRequest::getId).toList();
    }

    /**
     * Ends the employment of an approved request once its last working day has passed, acting as the
     * approver who decided it. Employment already ended by HR in the meantime is left as it is, and a last
     * working day HR has since moved waits for that day.
     */
    public void complete(Long requestId) {
        ResignationRequest request = resignationRequestRepository.getRequiredForUpdate(requestId);
        if (request.getStatus() != ResignationRequestStatus.APPROVED || request.getCompletedAt() != null) {
            return;
        }
        Employee employee = request.getEmployee();
        if (!employee.getEmploymentStatus().isEnded()) {
            // HR moved the last working day: the employee's own schedule ends it, and this request completes after.
            // A request approved before approvals scheduled the end has no day on the employee, and ends here.
            LocalDate scheduled = employee.getTerminationDate();
            if (scheduled != null && !scheduled.equals(request.getLastWorkingDate())) {
                return;
            }
            employmentLifecycleService.endEmploymentAs(employee, EmploymentStatus.RESIGNED, request.getLastWorkingDate(),
                request.getReason(), request.getReviewedByAccount());
        }
        Instant now = Instant.now(clock);
        request.setCompletedAt(now);
    }

    private void requireLastWorkingDate(LocalDate lastWorkingDate, Employee employee) {
        LocalDate today = LocalDate.now(clock);
        if (lastWorkingDate.isBefore(today) || lastWorkingDate.isBefore(employee.getHireDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "The last working day must be today or later", "lastWorkingDate");
        }
    }

    private void review(ResignationRequest request, ResignationRequestStatus status, @Nullable String comment) {
        Instant now = Instant.now(clock);
        request.setStatus(status);
        request.setReviewedByAccount(currentAccountProvider.account());
        request.setReviewComment(Strings.trimToNull(comment));
        request.setReviewedAt(now);
    }

    private boolean isApprover(ResignationRequest request) {
        Employee self = currentAccountProvider.account().getEmployee();
        return self != null && request.getApproverEmployee().getId().equals(self.getId());
    }

    private void requireApprover(ResignationRequest request) {
        if (!isApprover(request)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Only the approver of this resignation can decide it");
        }
    }

    private void requirePending(ResignationRequest request) {
        if (request.getStatus() != ResignationRequestStatus.PENDING) {
            throw new ConflictException(ErrorCode.REQUEST_ALREADY_PROCESSED,
                "Resignation request must be PENDING but was " + request.getStatus());
        }
    }

    private void requireSelfOrScopedAccess(Long employeeId, String scopedPermission) {
        if (!currentAccountProvider.account().belongsTo(employeeId)) {
            employeeAccessScopeService.requireEmployeeAccess(employeeId, scopedPermission);
        }
    }

    private ResignationRequestView toView(ResignationRequest request) {
        return new ResignationRequestView(
            request.getId(),
            request.getEmployee().getId(),
            request.getEmployee().getEmployeeCode(),
            request.getEmployee().getFullName(),
            request.getLastWorkingDate(),
            request.getReason(),
            request.getStatus(),
            request.getApproverEmployee().getId(),
            request.getApproverEmployee().getFullName(),
            request.getReviewComment(),
            request.getReviewedAt(),
            request.getCompletedAt(),
            request.getCreatedAt()
        );
    }

    /** Bộ lọc danh sách đơn nghỉ việc; trường null là không lọc. */
    public record ResignationRequestSearch(
        @Nullable String keyword,
        @Nullable Long organizationUnitId,
        @Nullable ResignationRequestStatus status
    ) { }

    public record ResignationRequestView(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate lastWorkingDate,
        String reason,
        ResignationRequestStatus status,
        Long approverEmployeeId,
        String approverEmployeeName,
        @Nullable String reviewComment,
        @Nullable Instant reviewedAt,
        /** When employment ended; null until the day after the last working day. */
        @Nullable Instant completedAt,
        Instant createdAt
    ) { }
}
