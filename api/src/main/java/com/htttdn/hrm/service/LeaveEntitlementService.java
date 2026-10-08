package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeLeaveEntitlement;
import com.htttdn.hrm.entity.LeaveEntitlementRule;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeLeaveEntitlementRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.LeaveEntitlementRuleRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

/**
 * Annual leave quota. Only {@link LeaveType#ANNUAL} draws on it; sick, maternity, unpaid and
 * other leave are tracked by their own workflows and never reduce the balance.
 */
@Service
@Transactional
public class LeaveEntitlementService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int FALLBACK_STANDARD_DAY_MINUTES = 480;
    private static final String REQUEST_READ = "request.read";
    private static final String REQUEST_MANAGE = "request.manage";

    private static final List<LeaveRequestStatus> CONSUMING_STATUSES =
        List.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.PENDING);
    private static final List<LeaveRequestStatus> APPROVED_ONLY =
        List.of(LeaveRequestStatus.APPROVED);

    private final EmployeeLeaveEntitlementRepository entitlementRepository;
    private final LeaveEntitlementRuleRepository ruleRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AccountRepository accountRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;

    public LeaveEntitlementService(
        EmployeeLeaveEntitlementRepository entitlementRepository,
        LeaveEntitlementRuleRepository ruleRepository,
        LeaveRequestRepository leaveRequestRepository,
        EmployeeRepository employeeRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AccountRepository accountRepository,
        CurrentAccountProvider currentAccountProvider,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.entitlementRepository = entitlementRepository;
        this.ruleRepository = ruleRepository;
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
        Employee employee = findEmployeeOrThrow(employeeId);
        EmployeeLeaveEntitlement entitlement = entitlementRepository
            .findByEmployeeIdAndYear(employeeId, (short) year)
            .orElse(null);

        int granted = entitlement != null
            ? entitlement.grantedMinutes()
            : computeBaseMinutes(employee, year, resolveRule(employee, year), standardDayMinutes(employeeId));
        int standardDay = entitlement != null
            ? entitlement.getStandardDayMinutes()
            : standardDayMinutes(employeeId);

        return buildBalance(employeeId, year, granted, standardDay, entitlement, null);
    }

    @PreAuthorize("hasAuthority('request.manage')")
    public BalanceView adjust(Long employeeId, int year, int adjustmentMinutes, String reason) {
        employeeAccessScopeService.requireEmployeeAccess(employeeId, REQUEST_MANAGE);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reason is required", "reason");
        }
        EmployeeLeaveEntitlement entitlement = materialize(employeeId, year);
        if (entitlement.grantedMinutes() - entitlement.getAdjustmentMinutes() + adjustmentMinutes < 0) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "Adjustment would make the yearly entitlement negative",
                "adjustmentMinutes"
            );
        }
        entitlement.setAdjustmentMinutes(adjustmentMinutes);
        entitlement.setAdjustmentReason(reason.trim());
        entitlement.setUpdatedAt(Instant.now());
        return buildBalance(
            employeeId, year, entitlement.grantedMinutes(), entitlement.getStandardDayMinutes(), entitlement, null
        );
    }

    @PreAuthorize("hasAuthority('request.manage')")
    public BalanceView setCarriedOver(Long employeeId, int year, int carriedOverMinutes, String reason) {
        employeeAccessScopeService.requireEmployeeAccess(employeeId, REQUEST_MANAGE);
        if (carriedOverMinutes < 0) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR, "carriedOverMinutes must not be negative", "carriedOverMinutes"
            );
        }
        EmployeeLeaveEntitlement entitlement = materialize(employeeId, year);
        entitlement.setCarriedOverMinutes(carriedOverMinutes);
        if (reason != null && !reason.isBlank()) {
            entitlement.setAdjustmentReason(reason.trim());
        }
        entitlement.setUpdatedAt(Instant.now());
        return buildBalance(
            employeeId, year, entitlement.grantedMinutes(), entitlement.getStandardDayMinutes(), entitlement, null
        );
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
        Long employeeId = request.getEmployee().getId();
        int year = request.getStartAt().atZone(BUSINESS_ZONE).getYear();
        EmployeeLeaveEntitlement entitlement = materialize(employeeId, year);
        BalanceView balance = buildBalance(
            employeeId, year, entitlement.grantedMinutes(), entitlement.getStandardDayMinutes(),
            entitlement, request.getId()
        );

        if (request.getRequestedMinutes() > balance.remainingMinutes()) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Annual leave balance for %d is not enough: %d minutes remaining, %d requested"
                    .formatted(year, balance.remainingMinutes(), request.getRequestedMinutes())
            );
        }
    }

    private EmployeeLeaveEntitlement materialize(Long employeeId, int year) {
        return entitlementRepository.findByEmployeeIdAndYear(employeeId, (short) year)
            .orElseGet(() -> createEntitlement(employeeId, year));
    }

    private EmployeeLeaveEntitlement createEntitlement(Long employeeId, int year) {
        Employee employee = findEmployeeOrThrow(employeeId);
        LeaveEntitlementRule rule = resolveRule(employee, year);
        int standardDay = standardDayMinutes(employeeId);
        Instant now = Instant.now();
        return entitlementRepository.save(EmployeeLeaveEntitlement.builder()
            .employee(employee)
            .year((short) year)
            .baseMinutes(computeBaseMinutes(employee, year, rule, standardDay))
            .carriedOverMinutes(0)
            .adjustmentMinutes(0)
            .standardDayMinutes(standardDay)
            .rule(rule)
            .createdByAccount(findCurrentAccount())
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    /**
     * Base days plus one block bonus per completed seniority block, prorated by the months worked
     * when the employee joined part-way through the year.
     */
    int computeBaseMinutes(Employee employee, int year, LeaveEntitlementRule rule, int standardDayMinutes) {
        if (rule == null) {
            return 0;
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        if (employee.getHireDate() != null && employee.getHireDate().isAfter(yearEnd)) {
            return 0;
        }

        LocalDate seniorityStart = Optional.ofNullable(employee.getSeniorityStartDate())
            .orElse(employee.getHireDate());
        long completedBlocks = 0;
        if (seniorityStart != null && !seniorityStart.isAfter(yearStart)) {
            long completedYears = ChronoUnit.YEARS.between(seniorityStart, yearStart);
            completedBlocks = completedYears / rule.getSeniorityBlockYears();
        }
        long days = rule.getBaseDays() + completedBlocks * rule.getSeniorityBonusDays();

        long totalMinutes = days * standardDayMinutes;
        if (employee.getHireDate() != null && !employee.getHireDate().isBefore(yearStart)) {
            long monthsWorked = 12 - employee.getHireDate().getMonthValue() + 1;
            totalMinutes = Math.round(totalMinutes * monthsWorked / 12.0);
        }
        return Math.toIntExact(totalMinutes);
    }

    private BalanceView buildBalance(
        Long employeeId,
        int year,
        int grantedMinutes,
        int standardDayMinutes,
        EmployeeLeaveEntitlement entitlement,
        Long excludedRequestId
    ) {
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
        long used = leaveRequestRepository.sumMinutesByTypeAndStatuses(
            employeeId, LeaveType.ANNUAL, APPROVED_ONLY, from, to, excludedRequestId
        );
        long committed = leaveRequestRepository.sumMinutesByTypeAndStatuses(
            employeeId, LeaveType.ANNUAL, CONSUMING_STATUSES, from, to, excludedRequestId
        );
        return new BalanceView(
            employeeId,
            year,
            grantedMinutes,
            entitlement == null ? 0 : entitlement.getCarriedOverMinutes(),
            entitlement == null ? 0 : entitlement.getAdjustmentMinutes(),
            entitlement == null ? null : entitlement.getAdjustmentReason(),
            standardDayMinutes,
            Math.toIntExact(used),
            Math.toIntExact(committed - used),
            Math.toIntExact(grantedMinutes - committed)
        );
    }

    private LeaveEntitlementRule resolveRule(Employee employee, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate anchor = employee.getHireDate() != null && employee.getHireDate().isAfter(yearStart)
            ? employee.getHireDate()
            : yearStart;
        return ruleRepository.findEffectiveAt(anchor).orElse(null);
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
        int carriedOverMinutes,
        int adjustmentMinutes,
        String adjustmentReason,
        int standardDayMinutes,
        int usedMinutes,
        int pendingMinutes,
        int remainingMinutes
    ) { }
}
