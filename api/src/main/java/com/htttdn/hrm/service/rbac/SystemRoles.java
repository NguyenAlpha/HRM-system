package com.htttdn.hrm.service.rbac;

/**
 * Codes of the seeded system roles that code refers to by name. They match {@code RoleSeeder} and cannot be
 * renamed or deleted through the role API.
 */
public final class SystemRoles {

    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String PAYROLL_APPROVER = "PAYROLL_APPROVER";
    public static final String DIRECTOR = "DIRECTOR";
    public static final String COMPANY_OWNER = "COMPANY_OWNER";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private SystemRoles() {
    }
}
