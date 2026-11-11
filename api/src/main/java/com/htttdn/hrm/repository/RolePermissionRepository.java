package com.htttdn.hrm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.RolePermission;
import com.htttdn.hrm.entity.RolePermissionId;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByIdRoleId(Long roleId);

    @Query("""
        SELECT rolePermission
        FROM RolePermission rolePermission
        JOIN FETCH rolePermission.permission permission
        WHERE rolePermission.role.id IN :roleIds
        ORDER BY rolePermission.role.id, permission.code
        """)
    List<RolePermission> findAllByRoleIds(@Param("roleIds") List<Long> roleIds);

    boolean existsByIdPermissionId(Long permissionId);

    boolean existsByIdRoleIdAndIdPermissionId(Long roleId, Long permissionId);

    /** Role có permission nào chỉ dùng được ở phạm vi toàn công ty không. */
    boolean existsByIdRoleIdAndPermissionCompanyWideTrue(Long roleId);

    /** Role có permission tự phục vụ nào không ({@link com.htttdn.hrm.entity.Permission#isSelfService}). */
    @Query("""
        SELECT CASE WHEN COUNT(rolePermission) > 0 THEN true ELSE false END
        FROM RolePermission rolePermission
        WHERE rolePermission.id.roleId = :roleId
          AND rolePermission.permission.code LIKE CONCAT('%', :marker, '%')
        """)
    boolean existsByRoleIdAndPermissionCodeContaining(@Param("roleId") Long roleId, @Param("marker") String marker);

    @Query("""
        SELECT rolePermission
        FROM RolePermission rolePermission
        JOIN FETCH rolePermission.permission permission
        WHERE rolePermission.role.id IN :roleIds
          AND permission.isActive = true
        """)
    List<RolePermission> findActiveByRoleIds(@Param("roleIds") List<Long> roleIds);
}
