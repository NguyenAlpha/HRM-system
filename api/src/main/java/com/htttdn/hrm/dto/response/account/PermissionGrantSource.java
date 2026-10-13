package com.htttdn.hrm.dto.response.account;

/**
 * One role assignment that gives a permission; {@code viaOverride} is true when the role itself
 * lacks it and a {@code GRANT} override on that assignment adds it.
 */
public record PermissionGrantSource(
    Long assignmentId,
    String roleCode,
    String roleName,
    boolean viaOverride
) {
}
