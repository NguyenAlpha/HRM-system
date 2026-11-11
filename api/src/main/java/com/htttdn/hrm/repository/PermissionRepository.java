package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.exception.ResourceNotFoundException;

public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByIsActiveTrueAndAssignmentPolicyOrderByModuleAscCodeAsc(
        PermissionAssignmentPolicy assignmentPolicy
    );

    /** Quyền theo id, không có thì 404 {@code PERMISSION_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default Permission getRequired(Long id) {
        return findById(id).orElseThrow(() -> new ResourceNotFoundException(
            ErrorCode.PERMISSION_NOT_FOUND, "Permission not found: " + id));
    }
}
