package com.htttdn.hrm.service.organization;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.jobposition.CreateJobPositionRequest;
import com.htttdn.hrm.dto.request.jobposition.UpdateJobPositionRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.jobposition.JobPositionResponse;
import com.htttdn.hrm.entity.JobPosition;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.JobPositionRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class JobPositionService {

    private final JobPositionRepository jobPositionRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final Clock clock;

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public List<JobPositionResponse> list(Boolean active, Boolean managerial) {
        return jobPositionRepository.findByDeletedAtIsNullOrderByTitleAsc().stream()
            .filter(position -> active == null || Objects.equals(position.getIsActive(), active))
            .filter(position -> managerial == null
                || Objects.equals(position.getIsManagerial(), managerial))
            .map(this::toResponse)
            .toList();
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public JobPositionResponse getById(Long positionId) {
        return toResponse(findPosition(positionId));
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public JobPositionResponse create(CreateJobPositionRequest request) {
        String code = request.code().trim();
        if (jobPositionRepository.existsByCodeAndDeletedAtIsNull(code)) {
            throw codeTaken();
        }
        JobPosition position = JobPosition.builder()
            .code(code)
            .title(request.title().trim())
            .description(normalizeNullable(request.description()))
            .isManagerial(request.isManagerial())
            .isActive(true)
            .build();
        try {
            return toResponse(jobPositionRepository.saveAndFlush(position));
        } catch (DataIntegrityViolationException exception) {
            throw codeTaken();
        }
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public JobPositionResponse update(Long positionId, UpdateJobPositionRequest request) {
        JobPosition position = findPositionForUpdate(positionId);
        position.setTitle(request.title().trim());
        position.setDescription(normalizeNullable(request.description()));
        position.setIsManagerial(request.isManagerial());
        position.setIsActive(request.isActive());
        return toResponse(jobPositionRepository.saveAndFlush(position));
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public void softDelete(Long positionId) {
        JobPosition position = findPositionForUpdate(positionId);
        if (employeeAssignmentRepository.existsCurrentOrFutureByPositionId(
            positionId,
            LocalDate.now(clock)
        )) {
            throw new ConflictException(
                ErrorCode.ORGANIZATION_RESOURCE_IN_USE,
                "Job position is used by a current or future employee assignment"
            );
        }

        Instant now = Instant.now(clock);
        position.setIsActive(false);
        position.setDeletedAt(now);
    }

    private JobPositionResponse toResponse(JobPosition position) {
        return new JobPositionResponse(
            position.getId(),
            position.getCode(),
            position.getTitle(),
            position.getDescription(),
            position.getIsManagerial(),
            position.getIsActive(),
            position.getCreatedAt(),
            position.getUpdatedAt()
        );
    }

    private JobPosition findPosition(Long positionId) {
        return jobPositionRepository.findByIdAndDeletedAtIsNull(positionId)
            .orElseThrow(() -> positionNotFound(positionId));
    }

    private JobPosition findPositionForUpdate(Long positionId) {
        return jobPositionRepository.findByIdForUpdate(positionId)
            .orElseThrow(() -> positionNotFound(positionId));
    }

    private ResourceNotFoundException positionNotFound(Long positionId) {
        return new ResourceNotFoundException(
            ErrorCode.JOB_POSITION_NOT_FOUND,
            "Job position not found: " + positionId
        );
    }

    private ConflictException codeTaken() {
        return new ConflictException(
            ErrorCode.JOB_POSITION_CODE_TAKEN,
            "Job position code is already taken",
            "code"
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
