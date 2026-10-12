package com.htttdn.hrm.repository.specification;

import org.springframework.data.jpa.domain.Specification;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.enums.AccountStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Search and filter conditions for the account list; each returns {@code null} when its filter is unset. */
public final class AccountSpecifications {

    private AccountSpecifications() {
    }

    /** Case-insensitive match on username, login email or the linked employee's name or code. */
    public static Specification<Account> matchesKeyword(String keyword) {
        String pattern = SearchPatterns.contains(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, builder) -> {
            Join<Account, Employee> employee = root.join("employee", JoinType.LEFT);
            return builder.or(
                builder.like(builder.lower(root.get("username")), pattern, SearchPatterns.ESCAPE),
                builder.like(builder.lower(root.get("email")), pattern, SearchPatterns.ESCAPE),
                builder.like(builder.lower(builder.coalesce(employee.get("fullName"), "")), pattern, SearchPatterns.ESCAPE),
                builder.like(builder.lower(builder.coalesce(employee.get("employeeCode"), "")), pattern, SearchPatterns.ESCAPE)
            );
        };
    }

    public static Specification<Account> hasStatus(AccountStatus status) {
        return status == null ? null : (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    /** Accounts holding a non-revoked assignment of the given role. */
    public static Specification<Account> hasRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return (root, query, builder) -> {
            Subquery<Long> assignment = query.subquery(Long.class);
            Root<AccountRoleAssignment> held = assignment.from(AccountRoleAssignment.class);
            assignment.select(held.get("id")).where(
                builder.equal(held.get("account"), root),
                builder.isNull(held.get("revokedAt")),
                builder.equal(held.get("role").get("code"), roleCode.trim())
            );
            return builder.exists(assignment);
        };
    }
}
