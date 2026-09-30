package com.htttdn.hrm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionModule;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByIsActiveTrueAndAssignmentPolicyOrderByModuleAscCodeAsc(
        PermissionAssignmentPolicy assignmentPolicy
    );

    Page<Permission> findByModule(PermissionModule module, Pageable pageable);
}
