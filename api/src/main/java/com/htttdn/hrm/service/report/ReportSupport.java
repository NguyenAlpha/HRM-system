package com.htttdn.hrm.service.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.report.DistributionItemResponse;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import lombok.RequiredArgsConstructor;

/**
 * Phần dùng chung của các báo cáo nhân sự và lương: chọn nhân viên theo phạm vi quyền và ngày, xét phạm vi
 * theo từng ngày cho báo cáo theo thời gian, và dựng dòng phân bố có phần trăm.
 *
 * <p>Là {@code @Component} không có {@code @Transactional} hay {@code @PreAuthorize}: chỉ được gọi từ các
 * service báo cáo, chạy trong transaction chỉ đọc và quyền đã kiểm tra ở đó.
 */
@Component
@RequiredArgsConstructor
public class ReportSupport {

    private final EmployeeRepository employeeRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final Clock clock;

    /**
     * Ngày chốt số liệu của báo cáo: không truyền → hôm nay.
     *
     * @throws BusinessException ngày ở tương lai (→ 400 {@code VALIDATION_ERROR}, field {@code asOfDate})
     */
    LocalDate asOfDate(LocalDate requestedDate) {
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

    /**
     * Nhân viên chưa xóa, đang làm việc vào {@code asOfDate} và nằm trong phạm vi {@code permissionCode} tại
     * ngày đó; lọc thêm theo trạng thái và đơn vị/địa điểm của phân công chính ngày đó (null → không lọc).
     */
    List<Employee> eligibleEmployees(
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

    /**
     * Những người có thể xuất hiện trong một báo cáo theo khoảng thời gian: chưa xóa, vào làm trước
     * {@code hiredBy} và chưa nghỉ trước {@code notEndedBefore}. Phạm vi quyền được xét riêng ở từng ngày
     * ({@link Population#visibleAt}), vì phân công của người đã nghỉ đã đóng: xét ở ngày họ còn làm thì role
     * theo đơn vị vẫn thấy người rời đơn vị mình.
     */
    Population population(String permissionCode, LocalDate notEndedBefore, LocalDate hiredBy) {
        EmployeeAccessScopeService.EmployeeAccessScope scope = employeeAccessScopeService.resolve(permissionCode);
        List<Employee> candidates = employeeRepository.findAll(notDeleted()
            .and(hiredBy(hiredBy))
            .and(notEndedBefore(notEndedBefore)));
        // Phạm vi toàn công ty: ai cũng thấy, dựng một lần. Phạm vi hẹp: mỗi ngày một query, nhớ lại để không query lặp.
        Function<LocalDate, Set<Long>> visibleAt;
        if (scope.companyWide()) {
            Set<Long> everyone = candidates.stream().map(Employee::getId).collect(Collectors.toSet());
            visibleAt = date -> everyone;
        } else {
            Map<LocalDate, Set<Long>> visibleCache = new HashMap<>();
            visibleAt = date -> visibleCache.computeIfAbsent(date, at -> employeeRepository
                .findAll(notDeleted().and(scope.toSpecification(at)))
                .stream().map(Employee::getId).collect(Collectors.toSet()));
        }
        return new Population(candidates, visibleAt);
    }

    /** Một dòng phân bố: số người và phần trăm trên tổng (2 chữ số; tổng 0 → 0%). */
    static DistributionItemResponse item(String key, String label, long count, long total) {
        BigDecimal percentage = total == 0
            ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new DistributionItemResponse(key, label, count, percentage);
    }

    /**
     * Ứng viên của báo cáo và hàm cho biết ai nằm trong phạm vi quyền ở một ngày.
     *
     * @param visibleAt id nhân viên trong phạm vi tại ngày đó
     */
    record Population(List<Employee> candidates, Function<LocalDate, Set<Long>> visibleAt) {

        /** Nhân viên này có nằm trong phạm vi quyền ở ngày {@code date} không. */
        boolean visible(Employee employee, LocalDate date) {
            return visibleAt.apply(date).contains(employee.getId());
        }

        /**
         * Quân số cuối ngày {@code date}: đã vào làm và chưa nghỉ; người nghỉ đúng ngày đó không còn tính,
         * nên quân số đầu kỳ + vào − nghỉ luôn bằng quân số cuối kỳ.
         */
        long headcount(LocalDate date) {
            Set<Long> visible = visibleAt.apply(date);
            return candidates.stream()
                .filter(employee -> !employee.getHireDate().isAfter(date))
                .filter(employee -> employee.getTerminationDate() == null || employee.getTerminationDate().isAfter(date))
                .filter(employee -> visible.contains(employee.getId()))
                .count();
        }
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

    /** Có phân công chính hiệu lực ngày {@code date} ở đơn vị/địa điểm đó (subquery EXISTS). */
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
}
