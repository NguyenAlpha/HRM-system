package com.htttdn.hrm.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.role.CreateRoleRequest;
import com.htttdn.hrm.dto.request.role.GrantPermissionRequest;
import com.htttdn.hrm.dto.request.role.UpdateRoleRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.dto.response.role.RoleResponse;
import com.htttdn.hrm.service.RoleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResult.ok(roleService.create(request));
    }

    @GetMapping
    public ApiResult<Page<RoleResponse>> list(@PageableDefault(sort = "id", size = 20) Pageable pageable) {
        return ApiResult.ok(roleService.list(pageable));
    }

    @GetMapping("/{id}")
    public ApiResult<RoleResponse> getById(@PathVariable Long id) {
        return ApiResult.ok(roleService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResult<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResult.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        roleService.softDelete(id);
        return ApiResult.ok();
    }

    @GetMapping("/{roleId}/permissions")
    public ApiResult<List<PermissionResponse>> listPermissions(@PathVariable Long roleId) {
        return ApiResult.ok(roleService.listPermissions(roleId));
    }

    @PostMapping("/{roleId}/permissions")
    public ApiResult<Void> grantPermission(
        @PathVariable Long roleId,
        @Valid @RequestBody GrantPermissionRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        roleService.grantPermission(roleId, request, accountId(jwt));
        return ApiResult.ok();
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ApiResult<Void> revokePermission(@PathVariable Long roleId, @PathVariable Long permissionId) {
        roleService.revokePermission(roleId, permissionId);
        return ApiResult.ok();
    }

    private Long accountId(Jwt jwt) {
        Object accountId = jwt.getClaim("accountId");
        if (accountId instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Authenticated token does not contain a valid accountId claim");
    }
}
