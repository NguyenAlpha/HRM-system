package com.htttdn.hrm.service.rbac;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.roleassignment.RoleAssignmentOptionResponse;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleAssignmentOptionService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentPolicy roleAssignmentPolicy;

    @PreAuthorize("hasAuthority('role.assignment.request')")
    public List<RoleAssignmentOptionResponse> listAvailableRoles() {
        return roleRepository.findByDeletedAtIsNullOrderByIdAsc().stream()
            .filter(roleAssignmentPolicy::isRequestable)
            .sorted(Comparator.comparing(Role::getName).thenComparing(Role::getCode))
            .map(this::toResponse)
            .toList();
    }

    private RoleAssignmentOptionResponse toResponse(Role role) {
        List<RoleScopeType> allowedScopeTypes = roleAssignmentPolicy.allowedScopes(role).stream()
            .sorted(Comparator.comparingInt(RoleScopeType::ordinal))
            .toList();
        return new RoleAssignmentOptionResponse(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.getGrantPolicy(),
            role.getGrantPolicy() == RoleGrantPolicy.OWNER_APPROVAL,
            allowedScopeTypes
        );
    }
}
