package com.htttdn.hrm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.response.permission.PermissionResponse;
import com.htttdn.hrm.entity.enums.PermissionModule;

public interface PermissionService {

    PermissionResponse getById(Long id);

    Page<PermissionResponse> list(Pageable pageable);

    Page<PermissionResponse> listByModule(PermissionModule module, Pageable pageable);
}
