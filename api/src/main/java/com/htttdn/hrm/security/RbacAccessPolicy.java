package com.htttdn.hrm.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class RbacAccessPolicy {

    private final Set<String> allowedAuthorities;

    public RbacAccessPolicy(
        @Value("${rbac.management.allowed-roles:SYSTEM_ADMIN}") List<String> allowedRoles
    ) {
        this.allowedAuthorities = allowedRoles.stream()
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .map(role -> "ROLE_" + role)
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean canManage(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(allowedAuthorities::contains);
    }
}
