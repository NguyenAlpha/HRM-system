package com.htttdn.hrm.service.attendance;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.attendance.AttendanceRecordResponse;
import com.htttdn.hrm.dto.response.attendance.TimesheetRowResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.specification.EmployeeSpecifications;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;

import lombok.RequiredArgsConstructor;

/**
 * Phần chỉ đọc của chấm công: một ngày công, công của một nhân viên và bảng công tháng. Ghi công nằm ở
 * {@link AttendanceService}, tăng ca ở {@link OvertimeService}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AttendanceQueryService {


    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrentAccountProvider currentAccountProvider;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final AttendanceMapper attendanceMapper;
    private final Clock clock;

    /**
     * Một ngày công. Nhân viên xem được của chính mình ({@code attendance.self.read}); HR/quản lý xem
     * được người trong phạm vi {@code attendance.read}.
     *
     * @throws com.htttdn.hrm.exception.ResourceNotFoundException không có ngày công (→ 404; tìm trước khi
     *         kiểm tra quyền)
     * @throws ForbiddenException không phải của mình và ngoài phạm vi (→ 403)
     */
    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    public AttendanceRecordResponse getById(Long id) {
        AttendanceRecord record = attendanceRecordRepository.getRequired(id);
        requireSelfOrScopedAccess(record.getEmployee().getId(), "attendance.self.read", "attendance.read");
        return attendanceMapper.toResponse(record);
    }

    /**
     * Các ngày công của một nhân viên trong khoảng ngày, phân trang; quyền xem như {@link #getById}.
     *
     * @throws ForbiddenException không phải của mình và ngoài phạm vi (→ 403)
     */
    @PreAuthorize("hasAnyAuthority('attendance.self.read', 'attendance.read')")
    public Page<AttendanceRecordResponse> listByEmployee(Long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        requireSelfOrScopedAccess(employeeId, "attendance.self.read", "attendance.read");
        return attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(employeeId, from, to, pageable)
            .map(attendanceMapper::toResponse);
    }

    /**
     * Bảng công tháng của mọi nhân viên trong scope {@code attendance.read} có làm việc trong tháng,
     * xếp theo mã nhân viên. Lọc thêm theo từ khóa (mã, tên, email) và đơn vị của phân công hiện tại.
     *
     * @param keyword từ khóa; rỗng/null → không lọc
     * @param organizationUnitId đơn vị; null → không lọc
     * @return mỗi nhân viên một dòng kèm các ngày công trong tháng; nhân viên chưa có ngày công nào vẫn
     *         có dòng, danh sách ngày rỗng
     */
    @PreAuthorize("hasAuthority('attendance.read')")
    public List<TimesheetRowResponse> timesheet(int year, int month, String keyword, Long organizationUnitId) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        // Có làm việc trong tháng: vào làm trước cuối tháng và chưa nghỉ trước đầu tháng. Phạm vi
        // (accessibleEmployees) đã bỏ nhân viên xóa mềm; matchesKeyword/currentlyAssignedTo trả null khi
        // không lọc → bỏ qua.
        Specification<Employee> employed = (root, query, builder) -> builder.and(
            builder.lessThanOrEqualTo(root.get("hireDate"), to),
            builder.or(builder.isNull(root.get("terminationDate")), builder.greaterThanOrEqualTo(root.get("terminationDate"), from))
        );
        Specification<Employee> specification = employeeAccessScopeService.accessibleEmployees("attendance.read").and(employed);
        for (Specification<Employee> filter : Arrays.asList(
            EmployeeSpecifications.matchesKeyword(keyword),
            EmployeeSpecifications.currentlyAssignedTo(organizationUnitId, null, LocalDate.now(clock))
        )) {
            if (filter != null) {
                specification = specification.and(filter);
            }
        }
        List<Employee> employees = employeeRepository.findAll(specification, Sort.by("employeeCode"));
        if (employees.isEmpty()) {
            return List.of();
        }
        // Một query lấy công của cả danh sách (không query từng người), gom thành Map<employeeId, ngày công>.
        Map<Long, List<AttendanceRecordResponse>> records = attendanceRecordRepository
            .findByEmployeeIdInAndWorkDateBetweenOrderByWorkDateAsc(employees.stream().map(Employee::getId).toList(), from, to)
            .stream()
            .collect(Collectors.groupingBy(record -> record.getEmployee().getId(),
                Collectors.mapping(attendanceMapper::toResponse, Collectors.toList())));
        return employees.stream()
            .map(employee -> new TimesheetRowResponse(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                records.getOrDefault(employee.getId(), List.of())))
            .toList();
    }

    /**
     * Cho qua khi là công của chính mình (có quyền self); không thì phải có quyền theo phạm vi và nhân
     * viên nằm trong phạm vi đó ({@code EmployeeAccessScopeService.requireEmployeeAccess}).
     *
     * @throws ForbiddenException không phải của mình và ngoài phạm vi (→ 403)
     */
    private void requireSelfOrScopedAccess(Long employeeId, String selfPermission, String scopedPermission) {
        if (currentAccountProvider.hasAuthority(selfPermission)) {
            Account actor = currentAccountProvider.account();
            if (actor.getEmployee() != null && employeeId.equals(actor.getEmployee().getId())) {
                return;
            }
        }
        if (!currentAccountProvider.hasAuthority(scopedPermission)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Employee is outside the assigned scope");
        }
        employeeAccessScopeService.requireEmployeeAccess(employeeId, scopedPermission);
    }
}
