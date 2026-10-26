package com.htttdn.hrm.service.leave;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.security.CurrentAccountProvider;

import lombok.RequiredArgsConstructor;

/**
 * Annual leave quota: 12 days a year, prorated by the months worked when the employee joined
 * part-way through the year (Bộ luật Lao động 2019, Điều 113). Nothing is stored; the balance is
 * computed from the hire date and the leave requests every time. Only {@link LeaveType#ANNUAL}
 * draws on it; sick, maternity, unpaid and other leave never reduce the balance. Unused days do
 * not carry over to the next year.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LeaveEntitlementService {

    static final int ANNUAL_LEAVE_DAYS = 12;
    private static final int FALLBACK_STANDARD_DAY_MINUTES = 480;
    private static final String REQUEST_READ = "request.read";

    private static final List<LeaveRequestStatus> CONSUMING_STATUSES =
        List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING);
    private static final List<LeaveRequestStatus> APPROVED_ONLY =
        List.of(LeaveRequestStatus.APPROVED);

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BalanceView getBalance(Long employeeId, int year) {
        requireSelfOrScopedAccess(employeeId);
        return buildBalance(employeeRepository.getRequired(employeeId), year, null);
    }

    /**
     * Phép năm của mọi nhân viên trong scope {@code report.hr.read} có làm việc trong năm, xếp theo mã.
     * Lọc thêm theo từ khóa (mã, tên, email) và đơn vị của phân công hiện tại.
     */
    @PreAuthorize("hasAuthority('report.hr.read')")
    @Transactional(readOnly = true)
    public List<EmployeeBalanceView> balancesForReport(int year, String keyword, Long organizationUnitId) {
        LocalDate first = LocalDate.of(year, 1, 1);
        LocalDate last = LocalDate.of(year, 12, 31);
        // Scope is checked afterwards, on each person's last day of the year, so leavers stay listed.
        Specification<Employee> notDeleted = (root, query, builder) -> builder.isNull(root.get("deletedAt"));
        Specification<Employee> specification = notDeleted
            .and((root, query, builder) -> builder.and(
                builder.lessThanOrEqualTo(root.get("hireDate"), last),
                builder.or(builder.isNull(root.get("terminationDate")),
                    builder.greaterThanOrEqualTo(root.get("terminationDate"), first))));
        for (Specification<Employee> filter : Arrays.asList(
            EmployeeSpecifications.matchesKeyword(keyword),
            EmployeeSpecifications.currentlyAssignedTo(organizationUnitId, null, LocalDate.now(clock))
        )) {
            if (filter != null) {
                specification = specification.and(filter);
            }
        }
        List<Employee> employees = employeeRepository.findAll(specification, Sort.by("employeeCode"));
        LocalDate today = LocalDate.now(clock);
        Set<Long> visible = employeeAccessScopeService.visibleOnLastDay("report.hr.read", employees,
            last.isBefore(today) ? last : today);
        return employees.stream()
            .filter(employee -> visible.contains(employee.getId()))
            .map(employee -> new EmployeeBalanceView(employee.getEmployeeCode(), employee.getFullName(),
                buildBalance(employee, year, null)))
            .toList();
    }

    /**
     * Rejects a request that would overdraw the year, checked when it is created, submitted and
     * approved. Other PENDING requests are counted as already spent, otherwise several requests
     * waiting together would each pass this check and only overshoot once they are all approved.
     */
    public void requireSufficientBalance(LeaveRequest request) {
        if (request.getLeaveType() != LeaveType.ANNUAL) {
            return;
        }
        int year = request.getStartAt().atZone(ClockConfig.BUSINESS_ZONE).getYear();
        BalanceView balance = buildBalance(request.getEmployee(), year, request.getId());

        if (request.getRequestedMinutes() > balance.remainingMinutes()) {
            throw new ConflictException(
                ErrorCode.INSUFFICIENT_LEAVE_BALANCE,
                "Annual leave balance for %d is not enough: %d minutes remaining, %d requested"
                    .formatted(year, balance.remainingMinutes(), request.getRequestedMinutes())
            );
        }
    }

    /**
     * 12 days, times the months worked out of 12 when the employee joined during the year; the
     * month of joining counts as a whole month.
     */
    static int grantedMinutes(Employee employee, int year, int standardDayMinutes) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate hireDate = employee.getHireDate();
        if (hireDate != null && hireDate.getYear() > year) {
            return 0;
        }
        int monthsWorked = hireDate != null && !hireDate.isBefore(yearStart)
            ? 12 - hireDate.getMonthValue() + 1
            : 12;
        return (int) Math.round(ANNUAL_LEAVE_DAYS * standardDayMinutes * monthsWorked / 12.0);
    }

    private BalanceView buildBalance(Employee employee, int year, Long excludedRequestId) {
        int standardDay = standardDayMinutes(employee.getId());
        int granted = grantedMinutes(employee, year, standardDay);
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
        long used = leaveRequestRepository.sumMinutesByTypeAndStatuses(
            employee.getId(), LeaveType.ANNUAL, APPROVED_ONLY, from, to, excludedRequestId
        );
        long committed = leaveRequestRepository.sumMinutesByTypeAndStatuses(
            employee.getId(), LeaveType.ANNUAL, CONSUMING_STATUSES, from, to, excludedRequestId
        );
        return new BalanceView(
            employee.getId(),
            year,
            granted,
            standardDay,
            Math.toIntExact(used),
            Math.toIntExact(committed - used),
            Math.toIntExact(granted - committed)
        );
    }

    private int standardDayMinutes(Long employeeId) {
        return employeeAssignmentRepository
            .findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull(employeeId)
            .map(assignment -> assignment.getShift() == null
                ? null
                : assignment.getShift().getStandardWorkMinutes())
            .orElse(FALLBACK_STANDARD_DAY_MINUTES);
    }

    private void requireSelfOrScopedAccess(Long employeeId) {
        Account account = currentAccountProvider.account();
        boolean isSelf = account.getEmployee() != null
            && account.getEmployee().getId().equals(employeeId);
        if (!isSelf) {
            employeeAccessScopeService.requireEmployeeAccess(employeeId, REQUEST_READ);
        }
    }

    /** Một dòng của báo cáo phép năm: nhân viên và số phép của năm đó. */
    public record EmployeeBalanceView(
        String employeeCode,
        String employeeName,
        BalanceView balance
    ) { }

    public record BalanceView(
        Long employeeId,
        int year,
        int grantedMinutes,
        int standardDayMinutes,
        int usedMinutes,
        int pendingMinutes,
        int remainingMinutes
    ) { }
}
