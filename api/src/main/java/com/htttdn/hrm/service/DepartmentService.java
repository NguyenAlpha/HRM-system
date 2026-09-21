package com.htttdn.hrm.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.htttdn.hrm.dto.request.department.CreateDepartmentRequest;
import com.htttdn.hrm.dto.request.department.UpdateDepartmentRequest;
import com.htttdn.hrm.dto.response.department.DepartmentResponse;

public interface DepartmentService {

    DepartmentResponse create(CreateDepartmentRequest request);

    DepartmentResponse getById(Long id);

    Page<DepartmentResponse> list(Pageable pageable);

    List<DepartmentResponse> listChildren(Long parentId);

    DepartmentResponse update(Long id, UpdateDepartmentRequest request);

    void softDelete(Long id);
}
