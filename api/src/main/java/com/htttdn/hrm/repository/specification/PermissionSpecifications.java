package com.htttdn.hrm.repository.specification;

import org.springframework.data.jpa.domain.Specification;

import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionModule;

/** Search and filter conditions for the permission catalogue; each returns {@code null} when its filter is unset. */
public final class PermissionSpecifications {

    private PermissionSpecifications() {
    }

    /** Case-insensitive match on the permission's code, name or description. */
    public static Specification<Permission> matchesKeyword(String keyword) {
        String pattern = SearchPatterns.contains(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, builder) -> builder.or(
            builder.like(builder.lower(root.get("code")), pattern, SearchPatterns.ESCAPE),
            builder.like(builder.lower(root.get("name")), pattern, SearchPatterns.ESCAPE),
            builder.like(builder.lower(root.get("description")), pattern, SearchPatterns.ESCAPE)
        );
    }

    public static Specification<Permission> inModule(PermissionModule module) {
        return module == null ? null : (root, query, builder) -> builder.equal(root.get("module"), module);
    }
}
