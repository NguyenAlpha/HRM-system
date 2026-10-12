package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

/**
 * Annual leave quota: 12 days a year, prorated by the months worked when the employee joined
 * part-way through the year (Bộ luật Lao động 2019, Điều 113). Nothing is stored; the balance is
 * computed from the hire date and the leave requests every time. Only {@link LeaveType#ANNUAL}
 * draws on it; sick, maternity, unpaid and other leave never reduce the balance. Unused days do
 * not carry over to the next year.
 */
@Service
@Transactional
public class LeaveEntitlementService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
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
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public LeaveEntitlementService(
        LeaveRequestRepository leaveRequestRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.accountRepository = accountRepository;
        this.currentAccountProvider = currentAccountProvider;
        this.employeeAccessScopeService = employeeAccessScopeService;
    }

    @Transactional(readOnly = true)
    public BalanceView getBalance(Long employeeId, int year) {
        requireSelfOrScopedAccess(employeeId);
        return buildBalance(findEmployeeOrThrow(employeeId), year, null);
    }

    /**
     * Rejects an approval that would overdraw the year. Other PENDING requests are counted as
     * already spent, otherwise several requests waiting together would each pass this check and
     * only overshoot once they are all approved.
     */
    public void requireSufficientBalance(LeaveRequest request) {
        if (request.getLeaveType() != LeaveType.ANNUAL) {
            return;
        }
        int year = request.getStartAt().atZone(BUSINESS_ZONE).getYear();
        BalanceView balance = buildBalance(request.getEmployee(), year, request.getId());

        if (request.getRequestedMinutes() > balance.remainingMinutes()) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
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
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
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
        Account account = findCurrentAccount();
        boolean isSelf = account.getEmployee() != null
            && account.getEmployee().getId().equals(employeeId);
        if (!isSelf) {
            employeeAccessScopeService.requireEmployeeAccess(employeeId, REQUEST_READ);
        }
    }

    private Employee findEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
            .filter(employee -> employee.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found: " + employeeId
            ));
    }

    private Account findCurrentAccount() {
        Long accountId = currentAccountProvider.accountId();
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.RESOURCE_NOT_FOUND, "Account not found: " + accountId
            ));
    }

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
