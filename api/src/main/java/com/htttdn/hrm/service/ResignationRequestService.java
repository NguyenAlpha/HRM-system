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

import com.htttdn.hrm.dto.request.employeerequest.CreateResignationRequestRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.ResignationRequest;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.ResignationRequestStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.ResignationRequestRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.repository.specification.SearchPatterns;
import com.htttdn.hrm.security.CurrentAccountProvider;

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
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "lastWorkingDate", "status");
    private static final Set<EmploymentStatus> ENDED = Set.of(
        EmploymentStatus.RESIGNED, EmploymentStatus.TERMINATED, EmploymentStatus.RETIRED);

    private final ResignationRequestRepository resignationRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final ApproverResolver approverResolver;
    private final EmployeeService employeeService;

    /** Sends the request to the employee's approver at once; one open request per employee. */
    @PreAuthorize("hasAnyAuthority('request.self.create', 'request.manage')")
    public ResignationRequestView create(CreateResignationRequestRequest command) {
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(command.employeeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + command.employeeId()));
        requireSelfOrScopedAccess(employee.getId(), REQUEST_MANAGE);
        if (ENDED.contains(employee.getEmploymentStatus())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Employee employment has already ended");
        }
        requireLastWorkingDate(command.requestedLastWorkingDate(), employee);
        if (resignationRequestRepository.existsOpen(employee.getId())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Employee already has an open resignation request");
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
        ResignationRequest request = findForUpdate(requestId);
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
        ResignationRequest request = findForUpdate(requestId);
        requireApprover(request);
        requirePending(request);
        if (ENDED.contains(request.getEmployee().getEmploymentStatus())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Employee employment has already ended");
        }
        LocalDate effective = lastWorkingDate != null ? lastWorkingDate : request.getLastWorkingDate();
        requireLastWorkingDate(effective, request.getEmployee());
        request.setLastWorkingDate(effective);
        review(request, ResignationRequestStatus.APPROVED, comment);
        return toView(request);
    }

    @PreAuthorize("isAuthenticated()")
    public ResignationRequestView reject(Long requestId, String comment) {
        ResignationRequest request = findForUpdate(requestId);
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
        ResignationRequest request = resignationRequestRepository.findById(requestId)
            .orElseThrow(() -> requestNotFound(requestId));
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
        Employee self = findCurrentAccount().getEmployee();
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
     * approver who decided it. Employment already ended by HR in the meantime is left as it is.
     */
    public void complete(Long requestId) {
        ResignationRequest request = findForUpdate(requestId);
        if (request.getStatus() != ResignationRequestStatus.APPROVED || request.getCompletedAt() != null) {
            return;
        }
        Employee employee = request.getEmployee();
        if (!ENDED.contains(employee.getEmploymentStatus())) {
            employeeService.endEmploymentAs(employee, EmploymentStatus.RESIGNED, request.getLastWorkingDate(),
                request.getReason(), request.getReviewedByAccount());
        }
        Instant now = Instant.now();
        request.setCompletedAt(now);
    }

    private void requireLastWorkingDate(LocalDate lastWorkingDate, Employee employee) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (lastWorkingDate.isBefore(today) || lastWorkingDate.isBefore(employee.getHireDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "The last working day must be today or later", "lastWorkingDate");
        }
    }

    private void review(ResignationRequest request, ResignationRequestStatus status, @Nullable String comment) {
        Instant now = Instant.now();
        request.setStatus(status);
        request.setReviewedByAccount(findCurrentAccount());
        request.setReviewComment(comment == null || comment.isBlank() ? null : comment.trim());
        request.setReviewedAt(now);
    }

    private boolean isApprover(ResignationRequest request) {
        Employee self = findCurrentAccount().getEmployee();
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
        Account actor = findCurrentAccount();
        if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
            return;
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, scopedPermission);
    }

    private ResignationRequest findForUpdate(Long requestId) {
        return resignationRequestRepository.findByIdForUpdate(requestId).orElseThrow(() -> requestNotFound(requestId));
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + accountId));
    }

    private static ResourceNotFoundException requestNotFound(Long requestId) {
        return new ResourceNotFoundException(ErrorCode.REQUEST_NOT_FOUND, "Resignation request not found: " + requestId);
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
