package com.htttdn.hrm.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.department.CreateDepartmentRequest;
import com.htttdn.hrm.dto.request.department.UpdateDepartmentRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.department.DepartmentResponse;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.service.DepartmentService;

@Service
@Transactional
public class DepartmentServiceImpl implements DepartmentService {

    private final OrganizationUnitRepository organizationUnitRepository;

    public DepartmentServiceImpl(OrganizationUnitRepository organizationUnitRepository) {
        this.organizationUnitRepository = organizationUnitRepository;
    }

    @Override
    public DepartmentResponse create(CreateDepartmentRequest request) {
        if (organizationUnitRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
            throw new ConflictException(ErrorCode.CONFLICT, "Department code is already taken", "code");
        }

        OrganizationUnit parentUnit = null;
        if (request.parentUnitId() != null) {
            parentUnit = findUnitOrThrow(request.parentUnitId());
        }

        Instant now = Instant.now();
        OrganizationUnit unit = OrganizationUnit.builder()
            .parentUnit(parentUnit)
            .code(request.code())
            .name(request.name())
            .unitType(request.unitType())
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return toResponse(organizationUnitRepository.save(unit));
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getById(Long id) {
        return toResponse(findUnitOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DepartmentResponse> list(Pageable pageable) {
        return organizationUnitRepository.findByDeletedAtIsNull(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listChildren(Long parentId) {
        return organizationUnitRepository.findByParentUnitIdAndDeletedAtIsNull(parentId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public DepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        OrganizationUnit unit = findUnitOrThrow(id);
        unit.setName(request.name());
        unit.setIsActive(request.isActive());
        unit.setUpdatedAt(Instant.now());
        return toResponse(unit);
    }

    @Override
    public void softDelete(Long id) {
        OrganizationUnit unit = findUnitOrThrow(id);
        unit.setDeletedAt(Instant.now());
        unit.setIsActive(false);
    }

    private OrganizationUnit findUnitOrThrow(Long id) {
        return organizationUnitRepository.findById(id)
            .filter(unit -> unit.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ORGANIZATION_UNIT_NOT_FOUND, "Organization unit not found: " + id));
    }

    private DepartmentResponse toResponse(OrganizationUnit unit) {
        return new DepartmentResponse(
            unit.getId(),
            unit.getParentUnit() != null ? unit.getParentUnit().getId() : null,
            unit.getCode(),
            unit.getName(),
            unit.getUnitType(),
            unit.getIsActive()
        );
    }
}
