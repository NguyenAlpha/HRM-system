package com.htttdn.hrm.service.leave;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.repository.specification.SearchPatterns;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.leave.LeaveRequestService.LeaveRequestView;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;

import jakarta.annotation.Nullable;

import lombok.RequiredArgsConstructor;

/**
 * Phần chỉ đọc của đơn nghỉ phép: xem một đơn, đơn của một nhân viên, tìm kiếm cho HR và hàng chờ duyệt
 * của người đang đăng nhập. Tạo, gửi, hủy, duyệt, từ chối nằm ở {@link LeaveRequestService}.
 *
 * <p>Ai được xem: chính nhân viên đó, người duyệt của đơn, hoặc người có {@code request.read} mà nhân viên
 * nằm trong phạm vi quyền ({@code EmployeeAccessScopeService}).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveRequestQueryService {

    private static final String REQUEST_READ = "request.read";
    /** Các cột được phép sắp xếp khi tìm kiếm; cột khác → 400 (SearchPatterns.requireSortable). */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("startAt", "createdAt", "submittedAt", "status");

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final Clock clock;

    /**
     * Một đơn nghỉ. Người duyệt của đơn luôn xem được, kể cả khi nhân viên nằm ngoài phạm vi quyền của họ.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có đơn (→ 404 {@code REQUEST_NOT_FOUND})
     * @throws com.htttdn.hrm.exception.ForbiddenException không phải của mình, không phải người duyệt và
     *         ngoài phạm vi {@code request.read} (→ 403)
     */
    @PreAuthorize("hasAnyAuthority('request.self.read', 'request.read')")
    public LeaveRequestView getById(Long requestId) {
        LeaveRequest request = leaveRequestRepository.getRequired(requestId);
        if (!request.isApprover(currentAccountProvider.account())) {
            requireSelfOrScopedAccess(request.getEmployee().getId());
        }
        return leaveRequestMapper.toView(request);
    }

    /**
     * Mọi đơn của một nhân viên, mới tạo trước. Đơn nháp chỉ chính nhân viên đó thấy, như ở {@link #search}.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException nhân viên không có hoặc đã xóa (→ 404)
     * @throws com.htttdn.hrm.exception.ForbiddenException không phải của mình và ngoài phạm vi (→ 403)
     */
    @PreAuthorize("hasAnyAuthority('request.self.read', 'request.read')")
    public Page<LeaveRequestView> listByEmployee(Long employeeId, Pageable pageable) {
        employeeRepository.getRequired(employeeId);
        if (currentAccountProvider.account().belongsTo(employeeId)) {
            return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable)
                .map(leaveRequestMapper::toView);
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, REQUEST_READ);
        return leaveRequestRepository.findByEmployeeIdAndStatusNotOrderByCreatedAtDesc(
            employeeId, LeaveRequestStatus.DRAFT, pageable).map(leaveRequestMapper::toView);
    }

    /**
     * Đơn nghỉ của mọi nhân viên trong scope {@code request.read}. Đơn nháp là của riêng người tạo
     * nên không bao giờ xuất hiện. {@code from}/{@code to} giữ các đơn có giao với khoảng ngày đó.
     *
     * @throws com.htttdn.hrm.exception.BusinessException sắp xếp theo cột không cho phép (→ 400
     *         {@code VALIDATION_ERROR})
     */
    @PreAuthorize("hasAuthority('request.read')")
    public Page<LeaveRequestView> search(LeaveRequestSearch search, Pageable pageable) {
        Specification<LeaveRequest> specification = (root, query, builder) ->
            builder.notEqual(root.get("status"), LeaveRequestStatus.DRAFT);

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
        if (search.leaveType() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("leaveType"), search.leaveType()));
        }
        // Ngày lọc theo giờ Việt Nam: from = 0h ngày from, to = 0h ngày sau ngày to (không lấy mốc đó).
        if (search.from() != null) {
            Instant fromStart = search.from().atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
            specification = specification.and((root, query, builder) -> builder.greaterThan(root.get("endAt"), fromStart));
        }
        if (search.to() != null) {
            Instant toEnd = search.to().plusDays(1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
            specification = specification.and((root, query, builder) -> builder.lessThan(root.get("startAt"), toEnd));
        }
        return leaveRequestRepository.findAll(specification, SearchPatterns.requireSortable(pageable, SORTABLE_PROPERTIES))
            .map(leaveRequestMapper::toView);
    }

    /**
     * The pending requests waiting for the signed-in employee's decision.
     *
     * @return rỗng khi tài khoản không gắn nhân viên (VD admin hệ thống)
     */
    @PreAuthorize("isAuthenticated()")
    public Page<LeaveRequestView> listPending(Pageable pageable) {
        Employee self = currentAccountProvider.account().getEmployee();
        if (self == null) {
            return Page.empty(pageable);
        }
        return leaveRequestRepository.findByApproverEmployeeIdAndStatusOrderByCreatedAtDesc(
            self.getId(), LeaveRequestStatus.PENDING, pageable).map(leaveRequestMapper::toView);
    }

    /** Cho qua đơn của chính mình; không thì nhân viên phải nằm trong phạm vi {@code request.read} (ngoài → 403). */
    private void requireSelfOrScopedAccess(Long employeeId) {
        if (!currentAccountProvider.account().belongsTo(employeeId)) {
            employeeAccessScopeService.requireEmployeeAccess(employeeId, REQUEST_READ);
        }
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
}
