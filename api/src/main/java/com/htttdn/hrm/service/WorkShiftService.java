package com.htttdn.hrm.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.workshift.CreateWorkShiftRequest;
import com.htttdn.hrm.dto.request.workshift.UpdateWorkShiftRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.workshift.WorkShiftResponse;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;
import com.htttdn.hrm.repository.WorkShiftRepository;

@Service
@Transactional
public class WorkShiftService {

    private static final long MINUTES_PER_DAY = 24L * 60L;

    private final WorkShiftRepository workShiftRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public WorkShiftService(
        WorkShiftRepository workShiftRepository,
        EmployeeAssignmentRepository employeeAssignmentRepository,
        AttendanceRecordRepository attendanceRecordRepository
    ) {
        this.workShiftRepository = workShiftRepository;
        this.employeeAssignmentRepository = employeeAssignmentRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public List<WorkShiftResponse> list(Boolean active, Boolean crossesMidnight) {
        return workShiftRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .filter(shift -> active == null || Objects.equals(shift.getIsActive(), active))
            .filter(shift -> crossesMidnight == null
                || Objects.equals(shift.getCrossesMidnight(), crossesMidnight))
            .map(this::toResponse)
            .toList();
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public WorkShiftResponse getById(Long shiftId) {
        return toResponse(findShift(shiftId));
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public WorkShiftResponse create(CreateWorkShiftRequest request) {
        String code = request.code().trim();
        if (workShiftRepository.existsByCodeAndDeletedAtIsNull(code)) {
            throw codeTaken();
        }
        validateSchedule(
            request.startTime(),
            request.endTime(),
            request.breakMinutes(),
            request.standardWorkMinutes(),
            request.graceLateMinutes(),
            request.crossesMidnight()
        );

        Instant now = Instant.now();
        WorkShift shift = WorkShift.builder()
            .code(code)
            .name(request.name().trim())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .breakMinutes(request.breakMinutes())
            .standardWorkMinutes(request.standardWorkMinutes())
            .graceLateMinutes(request.graceLateMinutes())
            .crossesMidnight(request.crossesMidnight())
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        try {
            return toResponse(workShiftRepository.saveAndFlush(shift));
        } catch (DataIntegrityViolationException exception) {
            throw codeTaken();
        }
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public WorkShiftResponse update(Long shiftId, UpdateWorkShiftRequest request) {
        WorkShift shift = findShiftForUpdate(shiftId);
        validateSchedule(
            request.startTime(),
            request.endTime(),
            request.breakMinutes(),
            request.standardWorkMinutes(),
            request.graceLateMinutes(),
            request.crossesMidnight()
        );
        if (scheduleChanged(shift, request) && attendanceRecordRepository.existsByShiftId(shiftId)) {
            throw resourceInUse(
                "Work shift schedule has attendance history; create a new shift instead"
            );
        }

        shift.setName(request.name().trim());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setBreakMinutes(request.breakMinutes());
        shift.setStandardWorkMinutes(request.standardWorkMinutes());
        shift.setGraceLateMinutes(request.graceLateMinutes());
        shift.setCrossesMidnight(request.crossesMidnight());
        shift.setIsActive(request.isActive());
        shift.setUpdatedAt(Instant.now());
        return toResponse(shift);
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public void softDelete(Long shiftId) {
        WorkShift shift = findShiftForUpdate(shiftId);
        if (employeeAssignmentRepository.existsCurrentOrFutureByShiftId(shiftId, LocalDate.now())) {
            throw resourceInUse("Work shift is used by a current or future employee assignment");
        }

        Instant now = Instant.now();
        shift.setIsActive(false);
        shift.setDeletedAt(now);
        shift.setUpdatedAt(now);
    }

    private void validateSchedule(
        LocalTime startTime,
        LocalTime endTime,
        int breakMinutes,
        int standardWorkMinutes,
        int graceLateMinutes,
        boolean crossesMidnight
    ) {
        if (!crossesMidnight && !endTime.isAfter(startTime)) {
            throw scheduleInvalid("A same-day shift must end after it starts", "endTime");
        }
        if (crossesMidnight && endTime.isAfter(startTime)) {
            throw scheduleInvalid(
                "An overnight shift must end at or before its start time on the next day",
                "endTime"
            );
        }

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        if (crossesMidnight) {
            durationMinutes += MINUTES_PER_DAY;
        }
        if (breakMinutes >= durationMinutes) {
            throw scheduleInvalid("breakMinutes must be shorter than the shift duration", "breakMinutes");
        }
        if (standardWorkMinutes > durationMinutes - breakMinutes) {
            throw scheduleInvalid(
                "standardWorkMinutes exceeds the shift duration after breaks",
                "standardWorkMinutes"
            );
        }
        if (graceLateMinutes > durationMinutes) {
            throw scheduleInvalid("graceLateMinutes exceeds the shift duration", "graceLateMinutes");
        }
    }

    private boolean scheduleChanged(WorkShift shift, UpdateWorkShiftRequest request) {
        return !shift.getStartTime().equals(request.startTime())
            || !shift.getEndTime().equals(request.endTime())
            || !shift.getBreakMinutes().equals(request.breakMinutes())
            || !shift.getStandardWorkMinutes().equals(request.standardWorkMinutes())
            || !shift.getGraceLateMinutes().equals(request.graceLateMinutes())
            || !shift.getCrossesMidnight().equals(request.crossesMidnight());
    }

    private WorkShiftResponse toResponse(WorkShift shift) {
        return new WorkShiftResponse(
            shift.getId(),
            shift.getCode(),
            shift.getName(),
            shift.getStartTime(),
            shift.getEndTime(),
            shift.getBreakMinutes(),
            shift.getStandardWorkMinutes(),
            shift.getGraceLateMinutes(),
            shift.getCrossesMidnight(),
            shift.getIsActive(),
            shift.getCreatedAt(),
            shift.getUpdatedAt()
        );
    }

    private WorkShift findShift(Long shiftId) {
        return workShiftRepository.findByIdAndDeletedAtIsNull(shiftId)
            .orElseThrow(() -> shiftNotFound(shiftId));
    }

    private WorkShift findShiftForUpdate(Long shiftId) {
        return workShiftRepository.findByIdForUpdate(shiftId)
            .orElseThrow(() -> shiftNotFound(shiftId));
    }

    private ResourceNotFoundException shiftNotFound(Long shiftId) {
        return new ResourceNotFoundException(
            ErrorCode.WORK_SHIFT_NOT_FOUND,
            "Work shift not found: " + shiftId
        );
    }

    private ConflictException codeTaken() {
        return new ConflictException(
            ErrorCode.WORK_SHIFT_CODE_TAKEN,
            "Work shift code is already taken",
            "code"
        );
    }

    private BusinessException scheduleInvalid(String message, String field) {
        return new BusinessException(ErrorCode.WORK_SHIFT_SCHEDULE_INVALID, message, field);
    }

    private ConflictException resourceInUse(String message) {
        return new ConflictException(ErrorCode.ORGANIZATION_RESOURCE_IN_USE, message);
    }
}
