package com.htttdn.hrm.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.htttdn.hrm.dto.response.common.ApiResult;
import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.service.PermissionService;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
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
}
