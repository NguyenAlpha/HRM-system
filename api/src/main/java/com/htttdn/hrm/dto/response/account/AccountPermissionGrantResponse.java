package com.htttdn.hrm.dto.response.account;

import java.util.List;

import com.htttdn.hrm.entity.enums.PermissionModule;

/** A permission an account holds today, with every role assignment that gives it. */
public record AccountPermissionGrantResponse(
    String code,
    String name,
    PermissionModule module,
    List<PermissionGrantSource> grantedBy
) {
}
