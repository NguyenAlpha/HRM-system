package com.htttdn.hrm.repository.specification;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.EmploymentStatus;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Search and filter conditions for the employee list; each returns {@code null} when its filter is unset. */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    /** Case-insensitive match on employee code, full name or work email. */
    public static Specification<Employee> matchesKeyword(String keyword) {
        String pattern = SearchPatterns.contains(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, builder) -> builder.or(
            builder.like(builder.lower(root.get("employeeCode")), pattern, SearchPatterns.ESCAPE),
            builder.like(builder.lower(root.get("fullName")), pattern, SearchPatterns.ESCAPE),
            builder.like(builder.lower(builder.coalesce(root.get("workEmail"), "")), pattern, SearchPatterns.ESCAPE)
        );
    }

    public static Specification<Employee> hasEmploymentStatus(EmploymentStatus status) {
        return status == null ? null : (root, query, builder) -> builder.equal(root.get("employmentStatus"), status);
    }

    /** Employees whose current primary assignment is in the given unit and/or position. */
    public static Specification<Employee> currentlyAssignedTo(Long organizationUnitId, Long positionId, LocalDate date) {
        if (organizationUnitId == null && positionId == null) {
            return null;
        }
        return (root, query, builder) -> {
            Subquery<Long> assignment = query.subquery(Long.class);
            Root<EmployeeAssignment> current = assignment.from(EmployeeAssignment.class);
            Predicate matches = builder.and(
                builder.equal(current.get("employee"), root),
                builder.isTrue(current.get("isPrimary")),
                builder.lessThanOrEqualTo(current.get("effectiveFrom"), date),
                builder.or(
                    builder.isNull(current.get("effectiveTo")),
                    builder.greaterThanOrEqualTo(current.get("effectiveTo"), date)
                )
            );
            if (organizationUnitId != null) {
                matches = builder.and(matches, builder.equal(current.get("organizationUnit").get("id"), organizationUnitId));
            }
            if (positionId != null) {
                matches = builder.and(matches, builder.equal(current.get("position").get("id"), positionId));
            }
            assignment.select(current.get("id")).where(matches);
            return builder.exists(assignment);
        };
    }
}
