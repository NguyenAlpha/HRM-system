package com.htttdn.hrm.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.request.permission.CreatePermissionRequest;
import com.htttdn.hrm.dto.request.permission.UpdatePermissionRequest;
import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.service.PermissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request) {
        return ApiResult.ok(permissionService.create(request));
    }

    @GetMapping
    public ApiResult<Page<PermissionResponse>> list(
        @RequestParam(required = false) PermissionModule module,
        @PageableDefault(sort = "id", size = 20) Pageable pageable
    ) {
        return ApiResult.ok(module == null
            ? permissionService.list(pageable)
            : permissionService.listByModule(module, pageable));
    }

    @GetMapping("/{id}")
    public ApiResult<PermissionResponse> getById(@PathVariable Long id) {
        return ApiResult.ok(permissionService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResult<PermissionResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdatePermissionRequest request
    ) {
        return ApiResult.ok(permissionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ApiResult.ok();
    }
}
