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

import com.htttdn.hrm.dto.request.worklocation.CreateWorkLocationRequest;
import com.htttdn.hrm.dto.request.worklocation.UpdateWorkLocationRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.worklocation.WorkLocationResponse;
import com.htttdn.hrm.dto.response.worklocation.WorkLocationTreeResponse;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.LocationType;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;

@Service
@Transactional
public class WorkLocationService {

    private final WorkLocationRepository workLocationRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final RoleAssignmentRequestRepository roleAssignmentRequestRepository;

    public WorkLocationService(
        WorkLocationRepository workLocationRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        RoleAssignmentRequestRepository roleAssignmentRequestRepository
    ) {
        this.workLocationRepository = workLocationRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.roleAssignmentRequestRepository = roleAssignmentRequestRepository;
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public List<WorkLocationResponse> list(
        Boolean active,
        LocationType locationType,
        Long parentLocationId
    ) {
        return workLocationRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .filter(location -> active == null || Objects.equals(location.getIsActive(), active))
            .filter(location -> locationType == null || location.getLocationType() == locationType)
            .filter(location -> parentLocationId == null
                || location.getParentLocation() != null
                    && location.getParentLocation().getId().equals(parentLocationId))
            .map(this::toResponse)
            .toList();
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public List<WorkLocationTreeResponse> tree(boolean includeInactive) {
        List<WorkLocation> locations = workLocationRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .filter(location -> includeInactive || Boolean.TRUE.equals(location.getIsActive()))
            .toList();
        Map<Long, List<WorkLocation>> childrenByParentId = new HashMap<>();
        List<WorkLocation> roots = new ArrayList<>();
        for (WorkLocation location : locations) {
            if (location.getParentLocation() == null) {
                roots.add(location);
            } else {
                childrenByParentId
                    .computeIfAbsent(location.getParentLocation().getId(), ignored -> new ArrayList<>())
                    .add(location);
            }
        }
        return roots.stream()
            .map(root -> toTreeResponse(root, childrenByParentId, new HashSet<>()))
            .toList();
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public WorkLocationResponse getById(Long locationId) {
        return toResponse(findLocation(locationId));
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public WorkLocationResponse create(CreateWorkLocationRequest request) {
        String code = request.code().trim();
        if (workLocationRepository.existsByCodeAndDeletedAtIsNull(code)) {
            throw codeTaken();
        }

        WorkLocation parent = resolveActiveParent(request.parentLocationId());
        validateHierarchy(request.locationType(), parent);
        Instant now = Instant.now();
        WorkLocation location = WorkLocation.builder()
            .parentLocation(parent)
            .code(code)
            .name(request.name().trim())
            .locationType(request.locationType())
            .address(request.address().trim())
            .phone(normalizeNullable(request.phone()))
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        try {
            return toResponse(workLocationRepository.saveAndFlush(location));
        } catch (DataIntegrityViolationException exception) {
            throw codeTaken();
        }
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public WorkLocationResponse update(Long locationId, UpdateWorkLocationRequest request) {
        WorkLocation location = findLocationForUpdate(locationId);
        WorkLocation parent = resolveActiveParent(request.parentLocationId());
        validateNoCycle(location, parent);
        validateHierarchy(location.getLocationType(), parent);
        if (Boolean.FALSE.equals(request.isActive())) {
            requireNoActiveChildren(locationId);
        }

        location.setParentLocation(parent);
        location.setName(request.name().trim());
        location.setAddress(request.address().trim());
        location.setPhone(normalizeNullable(request.phone()));
        location.setIsActive(request.isActive());
        location.setUpdatedAt(Instant.now());
        return toResponse(location);
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public void softDelete(Long locationId) {
        WorkLocation location = findLocationForUpdate(locationId);
        if (workLocationRepository.existsByParentLocationIdAndDeletedAtIsNull(locationId)) {
            throw resourceInUse("Work location still has child locations");
        }
        requireNotInUse(locationId);
        Instant now = Instant.now();
        location.setIsActive(false);
        location.setDeletedAt(now);
        location.setUpdatedAt(now);
    }

    private void requireNoActiveChildren(Long locationId) {
        boolean hasActiveChild = workLocationRepository
            .findByParentLocationIdAndDeletedAtIsNull(locationId).stream()
            .anyMatch(child -> Boolean.TRUE.equals(child.getIsActive()));
        if (hasActiveChild) {
            throw resourceInUse("Active child locations must be deactivated first");
        }
    }

    private void requireNotInUse(Long locationId) {
        LocalDate today = LocalDate.now();
        if (employeeAssignmentRepository.existsCurrentOrFutureByWorkLocationId(locationId, today)) {
            throw resourceInUse("Work location is used by a current or future employee assignment");
        }
        if (accountRoleAssignmentRepository.existsCurrentOrFutureByWorkLocationId(locationId, today)) {
            throw resourceInUse("Work location is used by a current or future role assignment");
        }
        if (roleAssignmentRequestRepository.existsByWorkLocationIdAndStatus(
            locationId,
            RoleAssignmentRequestStatus.PENDING
        )) {
            throw resourceInUse("Work location is used by a pending role assignment request");
        }
    }

    private WorkLocation resolveActiveParent(Long parentLocationId) {
        if (parentLocationId == null) {
            return null;
        }
        WorkLocation parent = findLocation(parentLocationId);
        if (!Boolean.TRUE.equals(parent.getIsActive())) {
            throw hierarchyInvalid("Parent work location must be active", "parentLocationId");
        }
        return parent;
    }

    private void validateHierarchy(LocationType locationType, WorkLocation parent) {
        boolean valid = switch (locationType) {
            case HEAD_OFFICE -> parent == null;
            case BRANCH -> parent != null && parent.getLocationType() == LocationType.HEAD_OFFICE;
            case WAREHOUSE -> parent != null
                && (parent.getLocationType() == LocationType.HEAD_OFFICE
                    || parent.getLocationType() == LocationType.BRANCH);
        };
        if (!valid) {
            throw hierarchyInvalid(
                "HEAD_OFFICE must be a root, BRANCH must belong to HEAD_OFFICE, and WAREHOUSE "
                    + "must belong to HEAD_OFFICE or BRANCH",
                "parentLocationId"
            );
        }
    }

    private void validateNoCycle(WorkLocation location, WorkLocation parent) {
        WorkLocation current = parent;
        while (current != null) {
            if (current.getId().equals(location.getId())) {
                throw hierarchyInvalid("Work location hierarchy cannot contain a cycle", "parentLocationId");
            }
            current = current.getParentLocation();
        }
    }

    private WorkLocationTreeResponse toTreeResponse(
        WorkLocation location,
        Map<Long, List<WorkLocation>> childrenByParentId,
        Set<Long> path
    ) {
        if (!path.add(location.getId())) {
            throw new IllegalStateException("Cycle detected in work location hierarchy");
        }
        List<WorkLocationTreeResponse> children = childrenByParentId
            .getOrDefault(location.getId(), List.of()).stream()
            .map(child -> toTreeResponse(child, childrenByParentId, new HashSet<>(path)))
            .toList();
        return new WorkLocationTreeResponse(
            location.getId(),
            location.getCode(),
            location.getName(),
            location.getLocationType(),
            location.getAddress(),
            location.getPhone(),
            location.getIsActive(),
            children
        );
    }

    private WorkLocationResponse toResponse(WorkLocation location) {
        WorkLocation parent = location.getParentLocation();
        return new WorkLocationResponse(
            location.getId(),
            parent == null ? null : parent.getId(),
            parent == null ? null : parent.getName(),
            location.getCode(),
            location.getName(),
            location.getLocationType(),
            location.getAddress(),
            location.getPhone(),
            location.getIsActive(),
            location.getCreatedAt(),
            location.getUpdatedAt()
        );
    }

    private WorkLocation findLocation(Long locationId) {
        return workLocationRepository.findByIdAndDeletedAtIsNull(locationId)
            .orElseThrow(() -> locationNotFound(locationId));
    }

    private WorkLocation findLocationForUpdate(Long locationId) {
        return workLocationRepository.findByIdForUpdate(locationId)
            .orElseThrow(() -> locationNotFound(locationId));
    }

    private ResourceNotFoundException locationNotFound(Long locationId) {
        return new ResourceNotFoundException(
            ErrorCode.LOCATION_NOT_FOUND,
            "Work location not found: " + locationId
        );
    }

    private ConflictException codeTaken() {
        return new ConflictException(
            ErrorCode.WORK_LOCATION_CODE_TAKEN,
            "Work location code is already taken",
            "code"
        );
    }

    private BusinessException hierarchyInvalid(String message, String field) {
        return new BusinessException(ErrorCode.ORGANIZATION_HIERARCHY_INVALID, message, field);
    }

    private ConflictException resourceInUse(String message) {
        return new ConflictException(ErrorCode.ORGANIZATION_RESOURCE_IN_USE, message);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
