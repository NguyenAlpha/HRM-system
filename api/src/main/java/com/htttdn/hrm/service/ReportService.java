package com.htttdn.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.report.DistributionItemResponse;
import com.htttdn.hrm.dto.response.report.HrWorkforceReportResponse;
import com.htttdn.hrm.dto.response.report.PayrollSalaryReportResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.EmployeeSalaryHistory;
import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
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

    public ReportService(
        EmployeeRepository employeeRepository,
        EmployeeSalaryHistoryRepository employeeSalaryHistoryRepository,
        EmployeeAccessScopeService employeeAccessScopeService
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeSalaryHistoryRepository = employeeSalaryHistoryRepository;
        this.employeeAccessScopeService = employeeAccessScopeService;
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
        LocalDate date = requestedDate == null ? LocalDate.now() : requestedDate;
        if (date.isAfter(LocalDate.now())) {
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
