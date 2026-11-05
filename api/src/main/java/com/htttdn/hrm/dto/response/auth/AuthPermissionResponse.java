package com.htttdn.hrm.dto.response.auth;

import com.htttdn.hrm.entity.enums.PermissionModule;

/**
 * A permission of the signed-in account. {@code companyScope} is true when a role assignment with scope
 * {@code COMPANY} gives it: only then do operations that need the whole company (payroll periods, preparing the
 * month's attendance, allowance policies) accept it, so the web shows them only on this flag.
 */
public record AuthPermissionResponse(
    String code,
    String name,
    PermissionModule module,
    boolean companyScope
) {
}
