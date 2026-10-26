package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationScope;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmployeeAccessScopeService {

    private final AccountAuthorizationService accountAuthorizationService;
    private final CurrentAccountProvider currentAccountProvider;
    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkLocationRepository workLocationRepository;
    private final Clock clock;

    public EmployeeAccessScope resolve(String permissionCode) {
        return resolve(currentAccountProvider.accountId(), permissionCode);
    }

    public boolean canAccountAccessEmployee(Long accountId, Long employeeId, String permissionCode) {
        EmployeeAccessScope scope = resolve(accountId, permissionCode);
        return employeeRepository.exists(notDeleted()
            .and(scope.toSpecification(LocalDate.now(clock)))
            .and(hasId(employeeId)));
    }

    private EmployeeAccessScope resolve(Long accountId, String permissionCode) {
        List<AuthorizationScope> assignedScopes = accountAuthorizationService.getScopes(accountId, permissionCode);

        boolean companyWide = assignedScopes.stream()
            .anyMatch(scope -> scope.scopeType() == RoleScopeType.COMPANY);
        Long selfEmployeeId = assignedScopes.stream()
            .anyMatch(scope -> scope.scopeType() == RoleScopeType.SELF)
                ? accountRepository.findById(accountId)
                    .map(Account::getEmployee)
                    .map(Employee::getId)
                    .orElse(null)
                : null;

        Set<Long> organizationUnitIds = new LinkedHashSet<>();
        Set<Long> workLocationIds = new LinkedHashSet<>();
        for (AuthorizationScope scope : assignedScopes) {
            if (scope.organizationUnitId() != null) {
                organizationUnitIds.addAll(descendantOrganizationUnitIds(scope.organizationUnitId()));
            }
            if (scope.workLocationId() != null) {
                workLocationIds.addAll(descendantWorkLocationIds(scope.workLocationId()));
            }
        }

        return new EmployeeAccessScope(companyWide, selfEmployeeId, organizationUnitIds, workLocationIds);
    }

    public void requireCompanyWide(String permissionCode) {
        if (!resolve(permissionCode).companyWide()) {
            throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "This operation requires COMPANY scope for " + permissionCode
            );
        }
    }

    public void requireEmployeeAccess(Long employeeId, String permissionCode) {
        EmployeeAccessScope scope = resolve(permissionCode);
        Specification<Employee> specification = notDeleted()
            .and(scope.toSpecification(LocalDate.now(clock)))
            .and(hasId(employeeId));
        if (!employeeRepository.exists(specification)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Employee is outside the assigned scope");
        }
    }

    public void requireDestinationAccess(Long organizationUnitId, Long workLocationId, String permissionCode) {
        if (!resolve(permissionCode).allowsDestination(organizationUnitId, workLocationId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Assignment destination is outside the assigned scope");
        }
    }

    public Specification<Employee> accessibleEmployees(String permissionCode) {
        return notDeleted().and(resolve(permissionCode).toSpecification(LocalDate.now(clock)));
    }

    /**
     * Ids of the given employees that were in the caller's scope on their last day up to {@code date}:
     * the termination date for someone who left earlier, the hire date for someone not yet started.
     * A scope by unit or location follows assignments, which close when an employee leaves, so
     * reports that cover past periods check each person on a day they were still assigned.
     */
    public Set<Long> visibleOnLastDay(String permissionCode, Collection<Employee> employees, LocalDate date) {
        EmployeeAccessScope scope = resolve(permissionCode);
        if (scope.companyWide()) {
            return employees.stream().map(Employee::getId).collect(Collectors.toSet());
        }
        // Ngày cuối còn làm tính đến date (không sớm hơn ngày vào làm) → nhóm theo ngày đó.
        Map<LocalDate, List<Long>> idsByDay = employees.stream().collect(Collectors.groupingBy(
            employee -> employee.employedFrom(employee.employedTo(date)),
            Collectors.mapping(Employee::getId, Collectors.toList())));
        Set<Long> visible = new LinkedHashSet<>();
        idsByDay.forEach((day, ids) -> employeeRepository.findAll(notDeleted()
                .and(scope.toSpecification(day))
                .and((root, query, builder) -> root.get("id").in(ids)))
            .forEach(employee -> visible.add(employee.getId())));
        return visible;
    }

    private Set<Long> descendantOrganizationUnitIds(Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            Long currentId = pending.removeFirst();
            if (!result.add(currentId)) {
                continue;
            }
            organizationUnitRepository.findByParentUnitIdAndDeletedAtIsNull(currentId).stream()
                .map(OrganizationUnit::getId)
                .forEach(pending::addLast);
        }
        return result;
    }

    private Set<Long> descendantWorkLocationIds(Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            Long currentId = pending.removeFirst();
            if (!result.add(currentId)) {
                continue;
            }
            workLocationRepository.findByParentLocationIdAndDeletedAtIsNull(currentId).stream()
                .map(WorkLocation::getId)
                .forEach(pending::addLast);
        }
        return result;
    }

    private Specification<Employee> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }

    private Specification<Employee> hasId(Long employeeId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), employeeId);
    }

    public record EmployeeAccessScope(
        boolean companyWide,
        Long selfEmployeeId,
        Set<Long> organizationUnitIds,
        Set<Long> workLocationIds
    ) {
        public EmployeeAccessScope {
            organizationUnitIds = Set.copyOf(organizationUnitIds);
            workLocationIds = Set.copyOf(workLocationIds);
        }

        public boolean allowsDestination(Long organizationUnitId, Long workLocationId) {
            return companyWide
                || organizationUnitIds.contains(organizationUnitId)
                || workLocationIds.contains(workLocationId);
        }

        public Specification<Employee> toSpecification(LocalDate date) {
            return (root, query, criteriaBuilder) -> {
                if (companyWide) {
                    return criteriaBuilder.conjunction();
                }

                List<Predicate> predicates = new java.util.ArrayList<>();
                if (selfEmployeeId != null) {
                    predicates.add(criteriaBuilder.equal(root.get("id"), selfEmployeeId));
                }
                if (!organizationUnitIds.isEmpty() || !workLocationIds.isEmpty()) {
                    Subquery<Long> assignmentQuery = query.subquery(Long.class);
                    Root<EmployeeAssignment> assignment = assignmentQuery.from(EmployeeAssignment.class);
                    List<Predicate> assignmentPredicates = new java.util.ArrayList<>();
                    assignmentPredicates.add(criteriaBuilder.equal(
                        assignment.get("employee").get("id"), root.get("id")
                    ));
                    assignmentPredicates.add(criteriaBuilder.isTrue(assignment.get("isPrimary")));
                    assignmentPredicates.add(criteriaBuilder.lessThanOrEqualTo(
                        assignment.get("effectiveFrom"), date
                    ));
                    assignmentPredicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(assignment.get("effectiveTo")),
                        criteriaBuilder.greaterThanOrEqualTo(assignment.get("effectiveTo"), date)
                    ));

                    List<Predicate> scopedAssignments = new java.util.ArrayList<>();
                    if (!organizationUnitIds.isEmpty()) {
                        scopedAssignments.add(assignment.get("organizationUnit").get("id").in(organizationUnitIds));
                    }
                    if (!workLocationIds.isEmpty()) {
                        scopedAssignments.add(assignment.get("workLocation").get("id").in(workLocationIds));
                    }
                    assignmentPredicates.add(criteriaBuilder.or(scopedAssignments.toArray(Predicate[]::new)));
                    assignmentQuery.select(assignment.get("id"))
                        .where(assignmentPredicates.toArray(Predicate[]::new));
                    predicates.add(criteriaBuilder.exists(assignmentQuery));
                }

                return predicates.isEmpty()
                    ? criteriaBuilder.disjunction()
                    : criteriaBuilder.or(predicates.toArray(Predicate[]::new));
            };
        }
    }
}
