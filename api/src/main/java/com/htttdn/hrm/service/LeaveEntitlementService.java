package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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

import jakarta.annotation.Nullable;

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

        return buildBalance(employeeId, year, granted, standardDay, entitlement, null, today());
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
            employeeId, year, entitlement.grantedMinutes(), entitlement.getStandardDayMinutes(), entitlement, null,
            today()
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
        applyCarriedOver(entitlement, carriedOverMinutes, carryOverExpiry(entitlement.getRule(), year));
        if (reason != null && !reason.isBlank()) {
            entitlement.setAdjustmentReason(reason.trim());
        }
        return buildBalance(
            employeeId, year, entitlement.grantedMinutes(), entitlement.getStandardDayMinutes(), entitlement, null,
            today()
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
        LocalDate startDate = request.getStartAt().atZone(BUSINESS_ZONE).toLocalDate();
        int year = startDate.getYear();
        EmployeeLeaveEntitlement entitlement = materialize(employeeId, year);
        BalanceView balance = buildBalance(
            employeeId, year, entitlement.grantedMinutes(), entitlement.getStandardDayMinutes(),
            entitlement, request.getId(), startDate
        );

        if (request.getRequestedMinutes() > balance.remainingMinutes()) {
            throw new ConflictException(
                ErrorCode.CONFLICT,
                "Annual leave balance for %d is not enough: %d minutes remaining, %d requested"
                    .formatted(year, balance.remainingMinutes(), request.getRequestedMinutes())
            );
        }
    }

    /** Year-end close preview: what each employee would carry from {@code fromYear} into the next year. */
    @PreAuthorize("hasAuthority('request.manage')")
    @Transactional(readOnly = true)
    public CarryOverView previewCarryOver(int fromYear) {
        employeeAccessScopeService.requireCompanyWide(REQUEST_MANAGE);
        return computeCarryOver(fromYear);
    }

    /**
     * Writes the preview into next year's entitlements. Running it again recomputes and overwrites,
     * so a correction made to {@code fromYear} afterwards can be carried by closing the year again.
     */
    @PreAuthorize("hasAuthority('request.manage')")
    public CarryOverView applyCarryOver(int fromYear) {
        employeeAccessScopeService.requireCompanyWide(REQUEST_MANAGE);
        CarryOverView view = computeCarryOver(fromYear);
        for (CarryOverItem item : view.items()) {
            EmployeeLeaveEntitlement entitlement = materialize(item.employeeId(), view.toYear());
            applyCarriedOver(entitlement, item.carriedOverMinutes(), view.expiresOn());
        }
        return view;
    }

    private CarryOverView computeCarryOver(int fromYear) {
        int toYear = fromYear + 1;
        LocalDate closingDate = LocalDate.of(fromYear, 12, 31);
        LocalDate nextYearStart = LocalDate.of(toYear, 1, 1);
        LeaveEntitlementRule rule = ruleRepository.findEffectiveAt(nextYearStart).orElse(null);
        int maxDays = rule == null ? 0 : rule.getMaxCarryOverDays();
        LocalDate expiresOn = carryOverExpiry(rule, toYear);

        List<CarryOverItem> items = employeeRepository.findEmployedDuring(nextYearStart, nextYearStart).stream()
            .filter(employee -> employee.getHireDate() == null || !employee.getHireDate().isAfter(closingDate))
            .sorted(Comparator.comparing(Employee::getEmployeeCode))
            .map(employee -> {
                EmployeeLeaveEntitlement source = entitlementRepository
                    .findByEmployeeIdAndYear(employee.getId(), (short) fromYear).orElse(null);
                int standardDay = source != null ? source.getStandardDayMinutes() : standardDayMinutes(employee.getId());
                int granted = source != null ? source.grantedMinutes()
                    : computeBaseMinutes(employee, fromYear, resolveRule(employee, fromYear), standardDay);
                int remaining = buildBalance(employee.getId(), fromYear, granted, standardDay, source, null,
                    closingDate).remainingMinutes();
                int carried = Math.max(0, Math.min(remaining, maxDays * standardDay));
                int current = entitlementRepository.findByEmployeeIdAndYear(employee.getId(), (short) toYear)
                    .map(EmployeeLeaveEntitlement::getCarriedOverMinutes).orElse(0);
                return new CarryOverItem(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                    remaining, standardDay, current, carried);
            })
            .toList();
        return new CarryOverView(fromYear, toYear, maxDays, expiresOn, items);
    }

    private void applyCarriedOver(EmployeeLeaveEntitlement entitlement, int carriedOverMinutes,
        @Nullable LocalDate expiresOn) {
        entitlement.setCarriedOverMinutes(carriedOverMinutes);
        entitlement.setCarriedOverExpiresOn(carriedOverMinutes > 0 ? expiresOn : null);
        entitlement.setUpdatedAt(Instant.now());
    }

    /** Last day of the rule's expiry month in {@code year}; null when no rule applies. */
    private static @Nullable LocalDate carryOverExpiry(@Nullable LeaveEntitlementRule rule, int year) {
        return rule == null ? null : YearMonth.of(year, rule.getCarryOverExpiryMonth()).atEndOfMonth();
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
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
        Long excludedRequestId,
        LocalDate asOf
    ) {
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(BUSINESS_ZONE).toInstant();
        int carriedOver = entitlement == null ? 0 : entitlement.getCarriedOverMinutes();
        LocalDate expiresOn = entitlement == null ? null : entitlement.getCarriedOverExpiresOn();
        int expiredCarriedOver = 0;
        if (carriedOver > 0 && expiresOn != null && asOf.isAfter(expiresOn)) {
            // Leave taken up to the expiry date draws on the carried-over days first; what is
            // left of them afterwards lapses.
            long usedBeforeExpiry = leaveRequestRepository.sumMinutesByTypeAndStatuses(
                employeeId, LeaveType.ANNUAL, CONSUMING_STATUSES, from,
                expiresOn.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant(), excludedRequestId
            );
            expiredCarriedOver = (int) Math.max(0, carriedOver - usedBeforeExpiry);
        }
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
            carriedOver,
            expiresOn,
            expiredCarriedOver,
            entitlement == null ? 0 : entitlement.getAdjustmentMinutes(),
            entitlement == null ? null : entitlement.getAdjustmentReason(),
            standardDayMinutes,
            Math.toIntExact(used),
            Math.toIntExact(committed - used),
            Math.toIntExact(grantedMinutes - expiredCarriedOver - committed)
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
        @Nullable LocalDate carriedOverExpiresOn,
        int expiredCarriedOverMinutes,
        int adjustmentMinutes,
        @Nullable String adjustmentReason,
        int standardDayMinutes,
        int usedMinutes,
        int pendingMinutes,
        int remainingMinutes
    ) { }

    public record CarryOverView(
        int fromYear,
        int toYear,
        int maxCarryOverDays,
        @Nullable LocalDate expiresOn,
        List<CarryOverItem> items
    ) { }

    public record CarryOverItem(
        Long employeeId,
        String employeeCode,
        String fullName,
        int remainingMinutes,
        int standardDayMinutes,
        int currentCarriedOverMinutes,
        int carriedOverMinutes
    ) { }
}
