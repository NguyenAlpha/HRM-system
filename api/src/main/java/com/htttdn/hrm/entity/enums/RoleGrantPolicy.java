package com.htttdn.hrm.entity.enums;

/**
 * Cách một role được phép cấp cho account.
 */
public enum RoleGrantPolicy {
    AUTO,
    HR_ASSIGNABLE,
    OWNER_APPROVAL,
    SYSTEM_ONLY
}
