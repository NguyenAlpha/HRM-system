package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.exception.ResourceNotFoundException;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByIsActiveTrueAndAssignmentPolicyOrderByModuleAscCodeAsc(
        PermissionAssignmentPolicy assignmentPolicy
    );

    Page<Permission> findByModule(PermissionModule module, Pageable pageable);

    /** Quyền theo id, không có thì 404 {@code PERMISSION_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default Permission getRequired(Long id) {
        return findById(id).orElseThrow(() -> new ResourceNotFoundException(
            ErrorCode.PERMISSION_NOT_FOUND, "Permission not found: " + id));
    }
}
