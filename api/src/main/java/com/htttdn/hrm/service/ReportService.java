package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.report.DistributionItemResponse;
import com.htttdn.hrm.dto.response.report.HrWorkforceReportResponse;
import com.htttdn.hrm.dto.response.report.MonthlyHrStatusReportResponse;
import com.htttdn.hrm.dto.response.report.PayrollSalaryReportResponse;
import com.htttdn.hrm.dto.response.report.TurnoverReportResponse;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.EmployeeSalaryHistoryRepository;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final String HR_REPORT_READ = "report.hr.read";
    private static final String PAYROLL_REPORT_READ = "report.payroll.read";
    private static final BigDecimal TEN_MILLION = new BigDecimal("10000000");
    private static final BigDecimal FIFTEEN_MILLION = new BigDecimal("15000000");
    private static final BigDecimal TWENTY_MILLION = new BigDecimal("20000000");
    private static final BigDecimal THIRTY_MILLION = new BigDecimal("30000000");

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final Clock clock;

    public ReportService(
        EmployeeRepository employeeRepository,
        EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository,
        EmployeeAccessScopeService employeeAccessScopeService,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AttendanceRecordRepository attendanceRecordRepository,
        Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeSalaryHistoryRepository = employeeSalaryHistoryRepository;
        this.employeeAccessScopeService = employeeAccessScopeService;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('report.hr.read')")
    public HrWorkforceReportResponse getWorkforceReport(
        LocalDate requestedDate,
        Long organizationUnitId,
        Long workLocationId,
        EmploymentStatus employmentStatus
    ) {
        LocalDate asOfDate = validateAsOfDate(requestedDate);
        List<Employee> employees = findEligibleEmployees(
            HR_REPORT_READ, asOfDate, organizationUnitId, workLocationId, employmentStatus
        );
        long total = employees.size();

        Map<EducationLevel, Long> educationCounts = new EnumMap<>(EducationLevel.class);
        for (EducationLevel level : EducationLevel.values()) {
            educationCounts.put(level, 0L);
        }
        long missingEducation = 0;
        long totalSeniorityMonths = 0;
        Map<String, Long> seniorityCounts = initializedSeniorityCounts();

        for (Employee employee : employees) {
            if (employee.getHighestEducationLevel() == null) {
                missingEducation++;
            } else {
                educationCounts.compute(employee.getHighestEducationLevel(), (key, value) -> value + 1);
            }

            long months = seniorityMonths(employee, asOfDate);
            totalSeniorityMonths += months;
            String bucket = seniorityBucket(months);
            seniorityCounts.compute(bucket, (key, value) -> value + 1);
        }

        List<DistributionItemResponse> education = new ArrayList<>();
        for (EducationLevel level : EducationLevel.values()) {
            education.add(item(level.name(), educationLabel(level), educationCounts.get(level), total));
        }
        education.add(item("NOT_UPDATED", "Chưa cập nhật", missingEducation, total));

        List<DistributionItemResponse> seniority = List.of(
            item("UNDER_ONE_YEAR", "Dưới 1 năm", seniorityCounts.get("UNDER_ONE_YEAR"), total),
            item("ONE_TO_THREE_YEARS", "1 – dưới 3 năm", seniorityCounts.get("ONE_TO_THREE_YEARS"), total),
            item("THREE_TO_FIVE_YEARS", "3 – dưới 5 năm", seniorityCounts.get("THREE_TO_FIVE_YEARS"), total),
            item("FIVE_TO_TEN_YEARS", "5 – dưới 10 năm", seniorityCounts.get("FIVE_TO_TEN_YEARS"), total),
            item("TEN_YEARS_OR_MORE", "Từ 10 năm", seniorityCounts.get("TEN_YEARS_OR_MORE"), total)
        );

        BigDecimal averageYears = total == 0
            ? BigDecimal.ZERO.setScale(1)
            : BigDecimal.valueOf(totalSeniorityMonths)
                .divide(BigDecimal.valueOf(total * 12L), 1, RoundingMode.HALF_UP);

        return new HrWorkforceReportResponse(
            asOfDate, total, averageYears, education, seniority, missingEducation
        );
    }

    @PreAuthorize("hasAuthority('report.payroll.read')")
    public PayrollSalaryReportResponse getSalaryReport(
        LocalDate requestedDate,
        Long organizationUnitId,
        Long workLocationId,
        EmploymentStatus employmentStatus
    ) {
        LocalDate asOfDate = validateAsOfDate(requestedDate);
        List<Employee> employees = findEligibleEmployees(
            PAYROLL_REPORT_READ, asOfDate, organizationUnitId, workLocationId, employmentStatus
        );
        long total = employees.size();
        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
        List<EmployeeSalaryHistory> salaryHistories = employeeIds.isEmpty()
            ? List.of()
            : employeeSalaryHistoryRepository.findEffectiveForEmployees(employeeIds, asOfDate);

        Map<Long, BigDecimal> salaries = new HashMap<>();
        for (EmployeeSalaryHistory salaryHistory : salaryHistories) {
            Long employeeId = salaryHistory.getEmployee().getId();
            if (salaries.put(employeeId, salaryHistory.getBaseSalary()) != null) {
                throw new ConflictException(
                    ErrorCode.CONFLICT,
                    "Employee has overlapping basic salaries at " + asOfDate
                );
            }
        }

        Map<String, Long> salaryCounts = initializedSalaryCounts();
        BigDecimal totalSalary = BigDecimal.ZERO;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (Employee employee : employees) {
            BigDecimal salary = salaries.get(employee.getId());
            if (salary == null) {
                salaryCounts.compute("NOT_CONFIGURED", (key, value) -> value + 1);
                continue;
            }
            totalSalary = totalSalary.add(salary);
            minimum = minimum == null || salary.compareTo(minimum) < 0 ? salary : minimum;
            maximum = maximum == null || salary.compareTo(maximum) > 0 ? salary : maximum;
            String bucket = salaryBucket(salary);
            salaryCounts.compute(bucket, (key, value) -> value + 1);
        }

        long withSalary = salaries.size();
        long missingSalary = total - withSalary;
        BigDecimal average = withSalary == 0
            ? null
            : totalSalary.divide(BigDecimal.valueOf(withSalary), 2, RoundingMode.HALF_UP);
        List<DistributionItemResponse> distribution = List.of(
            item("UNDER_10M", "Dưới 10 triệu", salaryCounts.get("UNDER_10M"), total),
            item("FROM_10M_TO_15M", "10 – dưới 15 triệu", salaryCounts.get("FROM_10M_TO_15M"), total),
            item("FROM_15M_TO_20M", "15 – dưới 20 triệu", salaryCounts.get("FROM_15M_TO_20M"), total),
            item("FROM_20M_TO_30M", "20 – dưới 30 triệu", salaryCounts.get("FROM_20M_TO_30M"), total),
            item("FROM_30M", "Từ 30 triệu", salaryCounts.get("FROM_30M"), total),
            item("NOT_CONFIGURED", "Chưa cấu hình", missingSalary, total)
        );

        return new PayrollSalaryReportResponse(
            asOfDate,
            "VND",
            total,
            withSalary,
            average,
            minimum,
            maximum,
            distribution,
            missingSalary
        );
    }

    /**
     * Biến động nhân sự của một năm. Phạm vi được xét tại từng ngày cần đếm (cuối tháng, ngày nghỉ việc)
     * vì phân công của người đã nghỉ đã đóng; nhờ vậy role theo đơn vị vẫn đếm được người rời đơn vị mình.
     */
    @PreAuthorize("hasAuthority('report.hr.read')")
    public TurnoverReportResponse getTurnoverReport(int year) {
        LocalDate today = LocalDate.now(clock);
        if (year > today.getYear()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "year must not be in the future", "year");
        }
        int lastMonth = year == today.getYear() ? today.getMonthValue() : 12;
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = yearStart.withMonth(lastMonth).withDayOfMonth(yearStart.withMonth(lastMonth).lengthOfMonth());

        EmployeeAccessScopeService.EmployeeAccessScope scope = employeeAccessScopeService.resolve(HR_REPORT_READ);
        List<Employee> candidates = employeeRepository.findAll(notDeleted()
            .and(hiredBy(yearEnd))
            .and(notEndedBefore(yearStart.minusDays(1))));
        Map<LocalDate, Set<Long>> visibleCache = new HashMap<>();
        Function<LocalDate, Set<Long>> visibleAt = date -> scope.companyWide()
            ? candidates.stream().map(Employee::getId).collect(Collectors.toSet())
            : visibleCache.computeIfAbsent(date, at -> employeeRepository.findAll(notDeleted().and(scope.toSpecification(at)))
                .stream().map(Employee::getId).collect(Collectors.toSet()));

        List<TurnoverReportResponse.TurnoverMonth> months = new ArrayList<>();
        List<TurnoverReportResponse.TurnoverEvent> events = new ArrayList<>();
        for (int month = 1; month <= lastMonth; month++) {
            LocalDate first = LocalDate.of(year, month, 1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            LocalDate previousEnd = first.minusDays(1);
            long start = headcount(candidates, previousEnd, visibleAt.apply(previousEnd));
            long end = headcount(candidates, last, visibleAt.apply(last));
            long hires = 0;
            long terminations = 0;
            for (Employee employee : candidates) {
                LocalDate hired = employee.getHireDate();
                LocalDate ended = employee.getTerminationDate();
                if (!hired.isBefore(first) && !hired.isAfter(last)) {
                    LocalDate seenOn = ended != null && ended.isBefore(last) ? ended : last;
                    if (visibleAt.apply(seenOn).contains(employee.getId())) {
                        hires++;
                        events.add(event(employee, "HIRE", hired));
                    }
                }
                if (ended != null && !ended.isBefore(first) && !ended.isAfter(last)
                    && visibleAt.apply(ended).contains(employee.getId())) {
                    terminations++;
                    events.add(event(employee, "TERMINATION", ended));
                }
            }
            months.add(new TurnoverReportResponse.TurnoverMonth(month, start, hires, terminations, end, rate(terminations, start, end)));
        }

        long headcountStart = months.getFirst().headcountStart();
        long headcountEnd = months.getLast().headcountEnd();
        long hires = months.stream().mapToLong(TurnoverReportResponse.TurnoverMonth::hires).sum();
        long terminations = months.stream().mapToLong(TurnoverReportResponse.TurnoverMonth::terminations).sum();
        events.sort(Comparator.comparing(TurnoverReportResponse.TurnoverEvent::date).thenComparing(TurnoverReportResponse.TurnoverEvent::employeeCode));
        return new TurnoverReportResponse(year, headcountStart, headcountEnd, hires, terminations,
            rate(terminations, headcountStart, headcountEnd), months, events);
    }

    /**
     * Tình hình nhân sự một tháng: ai đã làm việc trong tháng, ai vào, ai nghỉ việc, và mỗi người nghỉ
     * phép bao nhiêu ngày theo loại, vắng bao nhiêu ngày. Như biến động nhân sự, phạm vi được xét tại ngày
     * cuối tháng, hoặc ngày nghỉ việc với người nghỉ trong tháng. Tháng hiện tại tính cả đơn đã duyệt cho
     * những ngày sắp tới.
     */
    @PreAuthorize("hasAuthority('report.hr.read')")
    public MonthlyHrStatusReportResponse getMonthlyStatusReport(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        if (first.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "month must not be in the future", "month");
        }
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        LocalDate previousEnd = first.minusDays(1);

        EmployeeAccessScopeService.EmployeeAccessScope scope = employeeAccessScopeService.resolve(HR_REPORT_READ);
        List<Employee> candidates = employeeRepository.findAll(notDeleted()
            .and(hiredBy(last))
            .and(notEndedBefore(previousEnd)));
        Map<LocalDate, Set<Long>> visibleCache = new HashMap<>();
        Function<LocalDate, Set<Long>> visibleAt = date -> scope.companyWide()
            ? candidates.stream().map(Employee::getId).collect(Collectors.toSet())
            : visibleCache.computeIfAbsent(date, at -> employeeRepository.findAll(notDeleted().and(scope.toSpecification(at)))
                .stream().map(Employee::getId).collect(Collectors.toSet()));
        long headcountStart = headcount(candidates, previousEnd, visibleAt.apply(previousEnd));
        long headcountEnd = headcount(candidates, last, visibleAt.apply(last));

        // Người làm ít nhất một ngày trong tháng, xét phạm vi tại ngày cuối cùng họ còn làm trong tháng.
        Map<LocalDate, List<Employee>> bySeenOn = new HashMap<>();
        for (Employee employee : candidates) {
            LocalDate ended = employee.getTerminationDate();
            if (ended != null && ended.isBefore(first)) {
                continue;
            }
            LocalDate seenOn = ended != null && ended.isBefore(last) ? ended : last;
            if (visibleAt.apply(seenOn).contains(employee.getId())) {
                bySeenOn.computeIfAbsent(seenOn, date -> new ArrayList<>()).add(employee);
            }
        }
        Map<Long, EmployeeAssignment> assignments = new HashMap<>();
        bySeenOn.forEach((date, employees) -> employeeAssignmentRepository
            .findCurrentPrimaryByEmployeeIds(employees.stream().map(Employee::getId).toList(), date)
            .forEach(assignment -> assignments.put(assignment.getEmployee().getId(), assignment)));
        List<Employee> employees = bySeenOn.values().stream().flatMap(List::stream)
            .sorted(Comparator.comparing(Employee::getEmployeeCode)).toList();

        Map<Long, Map<LeaveType, BigDecimal>> leave = new HashMap<>();
        Map<Long, Long> absences = new HashMap<>();
        for (AttendanceRecord record : attendanceRecordRepository.findByEmployeeIdInAndWorkDateBetweenOrderByWorkDateAsc(
            employees.stream().map(Employee::getId).toList(), first, last)) {
            Long employeeId = record.getEmployee().getId();
            if (record.getLeaveRequest() != null && record.getLeaveMinutes() > 0) {
                BigDecimal days = BigDecimal.valueOf(record.getLeaveMinutes())
                    .divide(BigDecimal.valueOf(record.getScheduledMinutes()), 4, RoundingMode.HALF_UP);
                leave.computeIfAbsent(employeeId, id -> new EnumMap<>(LeaveType.class))
                    .merge(record.getLeaveRequest().getLeaveType(), days, BigDecimal::add);
            }
            if (record.getStatus() == AttendanceStatus.UNAUTHORIZED_ABSENCE) {
                absences.merge(employeeId, 1L, Long::sum);
            }
        }

        List<MonthlyHrStatusReportResponse.Row> rows = new ArrayList<>();
        long hires = 0;
        long terminations = 0;
        long onLeave = 0;
        BigDecimal leaveDays = BigDecimal.ZERO.setScale(2);
        long absentDays = 0;
        for (Employee employee : employees) {
            LocalDate ended = employee.getTerminationDate();
            boolean terminated = ended != null && !ended.isAfter(last);
            boolean hired = !employee.getHireDate().isBefore(first);
            hires += hired ? 1 : 0;
            terminations += terminated ? 1 : 0;
            Map<LeaveType, BigDecimal> byType = leave.getOrDefault(employee.getId(), Map.of());
            BigDecimal total = days(byType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            onLeave += total.signum() > 0 ? 1 : 0;
            leaveDays = leaveDays.add(total);
            long absent = absences.getOrDefault(employee.getId(), 0L);
            absentDays += absent;
            EmployeeAssignment assignment = assignments.get(employee.getId());
            rows.add(new MonthlyHrStatusReportResponse.Row(
                employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                assignment == null ? null : assignment.getOrganizationUnit().getName(),
                assignment == null ? null : assignment.getPosition().getTitle(),
                employee.getHireDate(), terminated ? ended : null,
                terminated ? "TERMINATED" : hired ? "NEW_HIRE" : "ACTIVE",
                terminated ? employee.getEmploymentStatus() : null,
                days(byType.get(LeaveType.ANNUAL)), days(byType.get(LeaveType.SICK)), days(byType.get(LeaveType.MATERNITY)),
                days(byType.get(LeaveType.UNPAID)), days(byType.get(LeaveType.OTHER)), total, absent));
        }
        return new MonthlyHrStatusReportResponse(year, month, headcountStart, hires, terminations, headcountEnd,
            onLeave, leaveDays, absentDays, rows);
    }

    /** Ngày nghỉ làm tròn 2 chữ số; không có thì 0. */
    private BigDecimal days(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Quân số cuối ngày {@code date}: đã vào làm và chưa nghỉ; người nghỉ đúng ngày đó không còn tính,
     * nên quân số đầu kỳ + vào − nghỉ luôn bằng quân số cuối kỳ.
     */
    private long headcount(List<Employee> employees, LocalDate date, Set<Long> visible) {
        return employees.stream()
            .filter(employee -> !employee.getHireDate().isAfter(date))
            .filter(employee -> employee.getTerminationDate() == null || employee.getTerminationDate().isAfter(date))
            .filter(employee -> visible.contains(employee.getId()))
            .count();
    }

    /** Số nghỉ việc chia quân số bình quân (đầu + cuối) / 2, tính theo phần trăm. */
    private BigDecimal rate(long terminations, long start, long end) {
        if (start + end == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(terminations * 200)
            .divide(BigDecimal.valueOf(start + end), 2, RoundingMode.HALF_UP);
    }

    private TurnoverReportResponse.TurnoverEvent event(Employee employee, String type, LocalDate date) {
        boolean termination = "TERMINATION".equals(type);
        return new TurnoverReportResponse.TurnoverEvent(employee.getId(), employee.getEmployeeCode(), employee.getFullName(), type, date,
            termination ? employee.getEmploymentStatus() : null, termination ? employee.getTerminationReason() : null);
    }

    private List<Employee> findEligibleEmployees(
        String permissionCode,
        LocalDate asOfDate,
        Long organizationUnitId,
        Long workLocationId,
        EmploymentStatus employmentStatus
    ) {
        Specification<Employee> specification = notDeleted()
            .and(employeeAccessScopeService.resolve(permissionCode).toSpecification(asOfDate))
            .and(hiredBy(asOfDate))
            .and(notEndedBefore(asOfDate));
        if (employmentStatus != null) {
            specification = specification.and(hasEmploymentStatus(employmentStatus));
        }
        if (organizationUnitId != null || workLocationId != null) {
            specification = specification.and(hasAssignmentAt(
                asOfDate, organizationUnitId, workLocationId
            ));
        }
        return employeeRepository.findAll(specification);
    }

    private Specification<Employee> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }

    private Specification<Employee> hiredBy(LocalDate date) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.lessThanOrEqualTo(root.get("hireDate"), date);
    }

    private Specification<Employee> notEndedBefore(LocalDate date) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
            criteriaBuilder.isNull(root.get("terminationDate")),
            criteriaBuilder.greaterThanOrEqualTo(root.get("terminationDate"), date)
        );
    }

    private Specification<Employee> hasEmploymentStatus(EmploymentStatus status) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("employmentStatus"), status);
    }

    private Specification<Employee> hasAssignmentAt(
        LocalDate date,
        Long organizationUnitId,
        Long workLocationId
    ) {
        return (root, query, criteriaBuilder) -> {
            Subquery<Long> assignmentQuery = query.subquery(Long.class);
            Root<EmployeeAssignment> assignment = assignmentQuery.from(EmployeeAssignment.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(assignment.get("employee").get("id"), root.get("id")));
            predicates.add(criteriaBuilder.isTrue(assignment.get("isPrimary")));
            predicates.add(criteriaBuilder.lessThanOrEqualTo(assignment.get("effectiveFrom"), date));
            predicates.add(criteriaBuilder.or(
                criteriaBuilder.isNull(assignment.get("effectiveTo")),
                criteriaBuilder.greaterThanOrEqualTo(assignment.get("effectiveTo"), date)
            ));
            if (organizationUnitId != null) {
                predicates.add(criteriaBuilder.equal(
                    assignment.get("organizationUnit").get("id"), organizationUnitId
                ));
            }
            if (workLocationId != null) {
                predicates.add(criteriaBuilder.equal(
                    assignment.get("workLocation").get("id"), workLocationId
                ));
            }
            assignmentQuery.select(assignment.get("id"))
                .where(predicates.toArray(Predicate[]::new));
            return criteriaBuilder.exists(assignmentQuery);
        };
    }

    private LocalDate validateAsOfDate(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? LocalDate.now(clock) : requestedDate;
        if (date.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "asOfDate must not be in the future",
                "asOfDate"
            );
        }
        return date;
    }

    private long seniorityMonths(Employee employee, LocalDate asOfDate) {
        LocalDate endDate = employee.getTerminationDate() != null
            && employee.getTerminationDate().isBefore(asOfDate)
                ? employee.getTerminationDate()
                : asOfDate;
        return Math.max(0, ChronoUnit.MONTHS.between(employee.getSeniorityStartDate(), endDate));
    }

    private String seniorityBucket(long months) {
        if (months < 12) return "UNDER_ONE_YEAR";
        if (months < 36) return "ONE_TO_THREE_YEARS";
        if (months < 60) return "THREE_TO_FIVE_YEARS";
        if (months < 120) return "FIVE_TO_TEN_YEARS";
        return "TEN_YEARS_OR_MORE";
    }

    private String salaryBucket(BigDecimal salary) {
        if (salary.compareTo(TEN_MILLION) < 0) return "UNDER_10M";
        if (salary.compareTo(FIFTEEN_MILLION) < 0) return "FROM_10M_TO_15M";
        if (salary.compareTo(TWENTY_MILLION) < 0) return "FROM_15M_TO_20M";
        if (salary.compareTo(THIRTY_MILLION) < 0) return "FROM_20M_TO_30M";
        return "FROM_30M";
    }

    private Map<String, Long> initializedSeniorityCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("UNDER_ONE_YEAR", 0L);
        counts.put("ONE_TO_THREE_YEARS", 0L);
        counts.put("THREE_TO_FIVE_YEARS", 0L);
        counts.put("FIVE_TO_TEN_YEARS", 0L);
        counts.put("TEN_YEARS_OR_MORE", 0L);
        return counts;
    }

    private Map<String, Long> initializedSalaryCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("UNDER_10M", 0L);
        counts.put("FROM_10M_TO_15M", 0L);
        counts.put("FROM_15M_TO_20M", 0L);
        counts.put("FROM_20M_TO_30M", 0L);
        counts.put("FROM_30M", 0L);
        counts.put("NOT_CONFIGURED", 0L);
        return counts;
    }

    private DistributionItemResponse item(String key, String label, long count, long total) {
        BigDecimal percentage = total == 0
            ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new DistributionItemResponse(key, label, count, percentage);
    }

    private String educationLabel(EducationLevel level) {
        return switch (level) {
            case HIGH_SCHOOL -> "Trung học phổ thông";
            case COLLEGE -> "Cao đẳng";
            case BACHELOR -> "Đại học";
            case MASTER -> "Thạc sĩ";
            case DOCTORATE -> "Tiến sĩ";
        };
    }
}
