package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.organizationunit.CreateOrganizationUnitRequest;
import com.htttdn.hrm.dto.request.organizationunit.UpdateOrganizationUnitRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.organizationunit.OrganizationUnitResponse;
import com.htttdn.hrm.dto.response.organizationunit.OrganizationUnitTreeResponse;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.enums.OrganizationUnitType;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;

@Service
@Transactional
public class OrganizationUnitService {

    private final OrganizationUnitRepository organizationUnitRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final RoleAssignmentRequestRepository roleAssignmentRequestRepository;

    public OrganizationUnitService(
        OrganizationUnitRepository organizationUnitRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        RoleAssignmentRequestRepository roleAssignmentRequestRepository
    ) {
        this.organizationUnitRepository = organizationUnitRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.roleAssignmentRequestRepository = roleAssignmentRequestRepository;
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public List<OrganizationUnitResponse> list(
        Boolean active,
        OrganizationUnitType unitType,
        Long parentUnitId
    ) {
        return organizationUnitRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .filter(unit -> active == null || Objects.equals(unit.getIsActive(), active))
            .filter(unit -> unitType == null || unit.getUnitType() == unitType)
            .filter(unit -> parentUnitId == null
                || unit.getParentUnit() != null && unit.getParentUnit().getId().equals(parentUnitId))
            .map(this::toResponse)
            .toList();
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public List<OrganizationUnitTreeResponse> tree(boolean includeInactive) {
        List<OrganizationUnit> units = organizationUnitRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .filter(unit -> includeInactive || Boolean.TRUE.equals(unit.getIsActive()))
            .toList();
        Map<Long, List<OrganizationUnit>> childrenByParentId = new HashMap<>();
        List<OrganizationUnit> roots = new ArrayList<>();
        for (OrganizationUnit unit : units) {
            if (unit.getParentUnit() == null) {
                roots.add(unit);
            } else {
                childrenByParentId
                    .computeIfAbsent(unit.getParentUnit().getId(), ignored -> new ArrayList<>())
                    .add(unit);
            }
        }
        return roots.stream()
            .map(root -> toTreeResponse(root, childrenByParentId, new HashSet<>()))
            .toList();
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public OrganizationUnitResponse getById(Long unitId) {
        return toResponse(findUnit(unitId));
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public OrganizationUnitResponse create(CreateOrganizationUnitRequest request) {
        String code = request.code().trim();
        if (organizationUnitRepository.existsByCodeAndDeletedAtIsNull(code)) {
            throw codeTaken();
        }

        OrganizationUnit parent = resolveActiveParent(request.parentUnitId());
        validateHierarchy(request.unitType(), parent);
        Instant now = Instant.now();
        OrganizationUnit unit = OrganizationUnit.builder()
            .parentUnit(parent)
            .code(code)
            .name(request.name().trim())
            .unitType(request.unitType())
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        try {
            return toResponse(organizationUnitRepository.saveAndFlush(unit));
        } catch (DataIntegrityViolationException exception) {
            throw codeTaken();
        }
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public OrganizationUnitResponse update(Long unitId, UpdateOrganizationUnitRequest request) {
        OrganizationUnit unit = findUnitForUpdate(unitId);
        OrganizationUnit parent = resolveActiveParent(request.parentUnitId());
        validateNoCycle(unit, parent);
        validateHierarchy(unit.getUnitType(), parent);
        if (Boolean.FALSE.equals(request.isActive())) {
            requireNoActiveChildren(unitId);
        }

        unit.setParentUnit(parent);
        unit.setName(request.name().trim());
        unit.setIsActive(request.isActive());
        unit.setUpdatedAt(Instant.now());
        return toResponse(unit);
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public void softDelete(Long unitId) {
        OrganizationUnit unit = findUnitForUpdate(unitId);
        if (organizationUnitRepository.existsByParentUnitIdAndDeletedAtIsNull(unitId)) {
            throw resourceInUse("Organization unit still has child units");
        }
        requireNotInUse(unitId);
        Instant now = Instant.now();
        unit.setIsActive(false);
        unit.setDeletedAt(now);
        unit.setUpdatedAt(now);
    }

    private void requireNoActiveChildren(Long unitId) {
        boolean hasActiveChild = organizationUnitRepository
            .findByParentUnitIdAndDeletedAtIsNull(unitId).stream()
            .anyMatch(child -> Boolean.TRUE.equals(child.getIsActive()));
        if (hasActiveChild) {
            throw resourceInUse("Active child units must be deactivated first");
        }
    }

    private void requireNotInUse(Long unitId) {
        LocalDate today = LocalDate.now();
        if (employeeAssignmentRepository.existsCurrentOrFutureByOrganizationUnitId(unitId, today)) {
            throw resourceInUse("Organization unit is used by a current or future employee assignment");
        }
        if (accountRoleAssignmentRepository.existsCurrentOrFutureByOrganizationUnitId(unitId, today)) {
            throw resourceInUse("Organization unit is used by a current or future role assignment");
        }
        if (roleAssignmentRequestRepository.existsByOrganizationUnitIdAndStatus(
            unitId,
            RoleAssignmentRequestStatus.PENDING
        )) {
            throw resourceInUse("Organization unit is used by a pending role assignment request");
        }
    }

    private OrganizationUnit resolveActiveParent(Long parentUnitId) {
        if (parentUnitId == null) {
            return null;
        }
        OrganizationUnit parent = findUnit(parentUnitId);
        if (!Boolean.TRUE.equals(parent.getIsActive())) {
            throw hierarchyInvalid("Parent organization unit must be active", "parentUnitId");
        }
        return parent;
    }

    private void validateHierarchy(OrganizationUnitType unitType, OrganizationUnit parent) {
        boolean valid = switch (unitType) {
            case BOARD -> parent == null;
            case DEPARTMENT -> parent != null && parent.getUnitType() == OrganizationUnitType.BOARD;
            case TEAM -> parent != null && parent.getUnitType() == OrganizationUnitType.DEPARTMENT;
        };
        if (!valid) {
            throw hierarchyInvalid(
                "BOARD must be a root, DEPARTMENT must belong to BOARD, and TEAM must belong to DEPARTMENT",
                "parentUnitId"
            );
        }
    }

    private void validateNoCycle(OrganizationUnit unit, OrganizationUnit parent) {
        OrganizationUnit current = parent;
        while (current != null) {
            if (current.getId().equals(unit.getId())) {
                throw hierarchyInvalid("Organization hierarchy cannot contain a cycle", "parentUnitId");
            }
            current = current.getParentUnit();
        }
    }

    private OrganizationUnitTreeResponse toTreeResponse(
        OrganizationUnit unit,
        Map<Long, List<OrganizationUnit>> childrenByParentId,
        Set<Long> path
    ) {
        if (!path.add(unit.getId())) {
            throw new IllegalStateException("Cycle detected in organization unit hierarchy");
        }
        List<OrganizationUnitTreeResponse> children = childrenByParentId
            .getOrDefault(unit.getId(), List.of()).stream()
            .map(child -> toTreeResponse(child, childrenByParentId, new HashSet<>(path)))
            .toList();
        return new OrganizationUnitTreeResponse(
            unit.getId(),
            unit.getCode(),
            unit.getName(),
            unit.getUnitType(),
            unit.getIsActive(),
            children
        );
    }

    private OrganizationUnitResponse toResponse(OrganizationUnit unit) {
        OrganizationUnit parent = unit.getParentUnit();
        return new OrganizationUnitResponse(
            unit.getId(),
            parent == null ? null : parent.getId(),
            parent == null ? null : parent.getName(),
            unit.getCode(),
            unit.getName(),
            unit.getUnitType(),
            unit.getIsActive(),
            unit.getCreatedAt(),
            unit.getUpdatedAt()
        );
    }

    private OrganizationUnit findUnit(Long unitId) {
        return organizationUnitRepository.findByIdAndDeletedAtIsNull(unitId)
            .orElseThrow(() -> unitNotFound(unitId));
    }

    private OrganizationUnit findUnitForUpdate(Long unitId) {
        return organizationUnitRepository.findByIdForUpdate(unitId)
            .orElseThrow(() -> unitNotFound(unitId));
    }

    private ResourceNotFoundException unitNotFound(Long unitId) {
        return new ResourceNotFoundException(
            ErrorCode.ORGANIZATION_UNIT_NOT_FOUND,
            "Organization unit not found: " + unitId
        );
    }

    private ConflictException codeTaken() {
        return new ConflictException(
            ErrorCode.ORGANIZATION_UNIT_CODE_TAKEN,
            "Organization unit code is already taken",
            "code"
        );
    }

    private BusinessException hierarchyInvalid(String message, String field) {
        return new BusinessException(ErrorCode.ORGANIZATION_HIERARCHY_INVALID, message, field);
    }

    private ConflictException resourceInUse(String message) {
        return new ConflictException(ErrorCode.ORGANIZATION_RESOURCE_IN_USE, message);
    }
}
