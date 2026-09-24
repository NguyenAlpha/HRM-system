package com.htttdn.hrm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByIdRoleId(Long roleId);

    boolean existsByIdPermissionId(Long permissionId);

    @Query("""
        SELECT rolePermission
        FROM RolePermission rolePermission
        JOIN FETCH rolePermission.permission permission
        WHERE rolePermission.role.id IN :roleIds
          AND permission.isActive = true
        """)
    List<RolePermission> findActiveByRoleIds(@Param("roleIds") List<Long> roleIds);
}
