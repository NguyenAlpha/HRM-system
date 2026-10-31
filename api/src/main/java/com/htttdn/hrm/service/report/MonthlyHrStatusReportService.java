package com.htttdn.hrm.service.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.report.MonthlyHrStatusReportResponse;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;

import lombok.RequiredArgsConstructor;

/** Báo cáo tình hình nhân sự một tháng: mỗi người làm trong tháng một dòng, kèm ngày nghỉ phép và ngày vắng. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MonthlyHrStatusReportService {

    private static final String HR_REPORT_READ = "report.hr.read";

    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ReportSupport reportSupport;
    private final Clock clock;

    /**
     * Tình hình nhân sự một tháng: ai đã làm việc trong tháng, ai vào, ai nghỉ việc, và mỗi người nghỉ
     * phép bao nhiêu ngày theo loại, vắng bao nhiêu ngày. Như biến động nhân sự, phạm vi được xét tại ngày
     * cuối tháng, hoặc ngày nghỉ việc với người nghỉ trong tháng. Tháng hiện tại tính cả đơn đã duyệt cho
     * những ngày sắp tới.
     *
     * <p>Ngày nghỉ = phút nghỉ / phút ca của từng ngày công, nên nghỉ nửa ngày là 0,5.
     *
     * @return quân số đầu/cuối tháng, số vào, số nghỉ, số người có nghỉ phép, tổng ngày nghỉ, tổng ngày vắng
     *         và các dòng theo mã nhân viên
     * @throws BusinessException tháng ở tương lai (→ 400 {@code VALIDATION_ERROR})
     */
    @PreAuthorize("hasAuthority('report.hr.read')")
    public MonthlyHrStatusReportResponse getMonthlyStatusReport(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        if (first.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "month must not be in the future", "month");
        }
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        LocalDate previousEnd = first.minusDays(1);

        ReportSupport.Population population = reportSupport.population(HR_REPORT_READ, previousEnd, last);
        long headcountStart = population.headcount(previousEnd);
        long headcountEnd = population.headcount(last);

        // Người làm ít nhất một ngày trong tháng, xét phạm vi tại ngày cuối cùng họ còn làm trong tháng.
        Map<LocalDate, List<Employee>> bySeenOn = new HashMap<>();
        for (Employee employee : population.candidates()) {
            LocalDate ended = employee.getTerminationDate();
            if (ended != null && ended.isBefore(first)) {
                continue;
            }
            LocalDate seenOn = ended != null && ended.isBefore(last) ? ended : last;
            if (population.visible(employee, seenOn)) {
                bySeenOn.computeIfAbsent(seenOn, date -> new ArrayList<>()).add(employee);
            }
        }
        // Đơn vị, chức vụ trên mỗi dòng: phân công chính ở ngày đó; một query cho mỗi nhóm cùng ngày.
        Map<Long, EmployeeAssignment> assignments = new HashMap<>();
        bySeenOn.forEach((date, employees) -> employeeAssignmentRepository
            .findCurrentPrimaryByEmployeeIds(employees.stream().map(Employee::getId).toList(), date)
            .forEach(assignment -> assignments.put(assignment.getEmployee().getId(), assignment)));
        List<Employee> employees = bySeenOn.values().stream().flatMap(List::stream)
            .sorted(Comparator.comparing(Employee::getEmployeeCode)).toList();

        Map<Long, Map<LeaveType, BigDecimal>> leave = new HashMap<>();
        Map<Long, Long> absences = new HashMap<>();
        for (AttendanceRecord record : attendanceRecordRepository.findByEmployeeIdInAndWorkDateBetweenOrderByWorkDateAsc(
            employees.stream().map(Employee::getId).toList(), first, last)) {
            Long employeeId = record.getEmployee().getId();
            if (record.getLeaveRequest() != null && record.getLeaveMinutes() > 0) {
                BigDecimal days = BigDecimal.valueOf(record.getLeaveMinutes())
                    .divide(BigDecimal.valueOf(record.getScheduledMinutes()), 4, RoundingMode.HALF_UP);
                leave.computeIfAbsent(employeeId, id -> new EnumMap<>(LeaveType.class))
                    .merge(record.getLeaveRequest().getLeaveType(), days, BigDecimal::add);
            }
            if (record.getStatus() == AttendanceStatus.UNAUTHORIZED_ABSENCE) {
                absences.merge(employeeId, 1L, Long::sum);
            }
        }

        List<MonthlyHrStatusReportResponse.Row> rows = new ArrayList<>();
        long hires = 0;
        long terminations = 0;
        long onLeave = 0;
        BigDecimal leaveDays = BigDecimal.ZERO.setScale(2);
        long absentDays = 0;
        for (Employee employee : employees) {
            LocalDate ended = employee.getTerminationDate();
            boolean terminated = ended != null && !ended.isAfter(last);
            boolean hired = !employee.getHireDate().isBefore(first);
            hires += hired ? 1 : 0;
            terminations += terminated ? 1 : 0;
            Map<LeaveType, BigDecimal> byType = leave.getOrDefault(employee.getId(), Map.of());
            BigDecimal total = days(byType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            onLeave += total.signum() > 0 ? 1 : 0;
            leaveDays = leaveDays.add(total);
            long absent = absences.getOrDefault(employee.getId(), 0L);
            absentDays += absent;
            EmployeeAssignment assignment = assignments.get(employee.getId());
            rows.add(new MonthlyHrStatusReportResponse.Row(
                employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                assignment == null ? null : assignment.getOrganizationUnit().getName(),
                assignment == null ? null : assignment.getPosition().getTitle(),
                employee.getHireDate(), terminated ? ended : null,
                terminated ? "TERMINATED" : hired ? "NEW_HIRE" : "ACTIVE",
                terminated ? employee.endStatus() : null,
                days(byType.get(LeaveType.ANNUAL)), days(byType.get(LeaveType.SICK)), days(byType.get(LeaveType.MATERNITY)),
                days(byType.get(LeaveType.UNPAID)), days(byType.get(LeaveType.OTHER)), total, absent));
        }
        return new MonthlyHrStatusReportResponse(year, month, headcountStart, hires, terminations, headcountEnd,
            onLeave, leaveDays, absentDays, rows);
    }

    /** Ngày nghỉ làm tròn 2 chữ số; không có thì 0. */
    private BigDecimal days(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
