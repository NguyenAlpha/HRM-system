package com.htttdn.hrm.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.role.CreateRoleRequest;
import com.htttdn.hrm.dto.request.role.GrantPermissionRequest;
import com.htttdn.hrm.dto.request.role.UpdateRoleRequest;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.dto.response.role.RoleResponse;
import com.htttdn.hrm.dto.response.role.RoleWithPermissionsResponse;

public interface RoleService {

    RoleResponse create(CreateRoleRequest request);

    RoleResponse getById(Long id);

    Page<RoleResponse> list(Pageable pageable);

    List<RoleWithPermissionsResponse> listWithPermissions();

    RoleResponse update(Long id, UpdateRoleRequest request);

    void softDelete(Long id);

    void grantPermission(Long roleId, GrantPermissionRequest request, Long grantedByAccountId);

    void revokePermission(Long roleId, Long permissionId);

    List<PermissionResponse> listPermissions(Long roleId);
}
