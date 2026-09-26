package com.htttdn.hrm.security;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacAccessPolicyTest {

    @Test
    void missingAuthenticationAndEmptyRoleConfigurationDenyAccess() {
        RbacAccessPolicy policy = new RbacAccessPolicy(List.of("SYSTEM_ADMIN"));
        assertFalse(policy.canManage(null));
        assertFalse(policy.canManage(UsernamePasswordAuthenticationToken.unauthenticated("admin", "unused")));
        assertFalse(new RbacAccessPolicy(List.of(" ")).canManage(
            UsernamePasswordAuthenticationToken.authenticated("admin", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")))));
    }

    @Test
    void configuredRoleCodesAreTrimmedAndComparedExactly() {
        RbacAccessPolicy policy = new RbacAccessPolicy(List.of(
            "SYSTEM_ADMIN",
            "COMPANY_OWNER",
            " RBAC_MANAGER "
        ));
        assertTrue(policy.canManage(UsernamePasswordAuthenticationToken.authenticated("owner", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_COMPANY_OWNER")))));
        assertTrue(policy.canManage(UsernamePasswordAuthenticationToken.authenticated("manager", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_RBAC_MANAGER")))));
        assertFalse(policy.canManage(UsernamePasswordAuthenticationToken.authenticated("employee", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN_ASSISTANT")))));
    }
}
