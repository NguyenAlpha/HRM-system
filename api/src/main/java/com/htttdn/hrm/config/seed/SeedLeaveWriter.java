package com.htttdn.hrm.config.seed;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.config.seed.StaffSeeder.AbsenceEntry;
import com.htttdn.hrm.config.seed.StaffSeeder.LeaveEntry;
import com.htttdn.hrm.config.seed.StaffSeeder.StaffEntry;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AttendanceRecord;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.LeaveRequest;
import com.htttdn.hrm.entity.Notification;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.entity.enums.AttendanceStatus;
import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.NotificationRepository;
import com.htttdn.hrm.service.attendance.AttendanceCalendarService;
import com.htttdn.hrm.service.attendance.AttendanceCalendarService.ScheduledDay;
import com.htttdn.hrm.service.attendance.AttendanceService;
import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import com.htttdn.hrm.service.leave.LeaveEntitlementService;

import lombok.RequiredArgsConstructor;

/**
 * Ghi đơn nghỉ và các ngày công khác thường của một nhân viên được seed, sau khi
 * {@link SeedAttendanceWriter} đã ghi chấm công {@code PRESENT}.
 *
 * <p>Ngày được chỉ bằng {@code workDaysAgo}: ngày làm việc thứ N tính lùi từ hôm qua (1 là ngày làm
 * việc gần nhất), số âm là ngày làm việc sắp tới (-1 là ngày làm việc đầu tiên sau hôm nay). Nhờ vậy
 * ngày luôn rơi vào ngày làm việc, không vào cuối tuần hay ngày lễ.
 *
 * <p>Đơn đã duyệt đi qua {@link AttendanceService#applyApprovedLeave} như khi người duyệt bấm duyệt,
 * nên trạng thái và số phút của ngày công khớp với luồng thật; đơn phép năm phải còn đủ phép. Ngày
 * vắng không phép là ngày HR đối soát; ngày thiếu chấm công chỉ ghi trong tháng hiện tại, để các
 * tháng trước vẫn tính lương được.
 *
 * <p>Thông báo được ghi như {@code NotificationService} gửi, nhưng theo ngày gửi và ngày quyết định của đơn:
 * người duyệt và HR Manager xem được đơn của nhân viên nhận thông báo đơn mới, nhân viên nhận kết quả duyệt.
 * Thông báo cũ hơn một tuần đã được đọc.
 */
@Component
@RequiredArgsConstructor
class SeedLeaveWriter {

    private static final int FUTURE_DAYS = 90;
    private static final String MORNING = "MORNING";
    private static final String AFTERNOON = "AFTERNOON";
    // Notifications older than this were read; newer ones still show as unread.
    private static final int READ_AFTER_DAYS = 7;

    private final AttendanceCalendarService attendanceCalendarService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceService attendanceService;
    private final LeaveEntitlementService leaveEntitlementService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final EmployeeAccessScopeService employeeAccessScopeService;
    private final NotificationRepository notificationRepository;
    private final SeedTimestamps seedTimestamps;

    /**
     * @param approverOn the account that approves the employee's requests on a date
     * @param reconciler the HR account that records unauthorized absences
     * @return the number of leave requests written
     */
    int write(StaffEntry entry, Employee employee, Function<LocalDate, Account> approverOn, Account reconciler) {
        LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
        List<ScheduledDay> past = attendanceCalendarService.scheduleFor(employee.getId(), employee.getHireDate(), today.minusDays(1));
        List<ScheduledDay> days = new ArrayList<>(past);
        days.addAll(attendanceCalendarService.scheduleFor(employee.getId(), today.plusDays(1), today.plusDays(FUTURE_DAYS)));
        Set<LocalDate> taken = new HashSet<>();
        Calendar calendar = new Calendar(entry, days, past.size(), taken);

        if (entry.absences() != null) {
            for (AbsenceEntry absence : entry.absences()) {
                ScheduledDay day = calendar.pastDay(absence.workDaysAgo(), "absences");
                AttendanceRecord record = recordOn(employee, day.workDate());
                clear(record);
                record.setStatus(AttendanceStatus.UNAUTHORIZED_ABSENCE);
                record.setNote(absence.note());
                record.setUpdatedByAccount(reconciler);
                seedTimestamps.set(record, null, day.endAt().plus(Duration.ofHours(16)));
            }
        }
        if (entry.missingPunches() != null) {
            YearMonth currentMonth = YearMonth.from(today);
            for (int workDaysAgo : entry.missingPunches()) {
                ScheduledDay day = calendar.pastDay(workDaysAgo, "missingPunches");
                // Past months stay complete so their payroll can be calculated.
                if (YearMonth.from(day.workDate()).equals(currentMonth)) {
                    AttendanceRecord record = recordOn(employee, day.workDate());
                    clear(record);
                    record.setStatus(AttendanceStatus.MISSING_PUNCH);
                }
            }
        }
        if (entry.leaves() == null) {
            return 0;
        }
        for (LeaveEntry leave : entry.leaves()) {
            writeLeave(entry, employee, leave, calendar, approverOn);
        }
        return entry.leaves().size();
    }

    private void writeLeave(StaffEntry entry, Employee employee, LeaveEntry leave, Calendar calendar,
        Function<LocalDate, Account> approverOn) {
        int count = leave.days() == null ? 1 : leave.days();
        if (leave.type() == null || leave.status() == null || leave.reason() == null || count < 1
            || leave.part() != null && (count != 1 || !MORNING.equals(leave.part()) && !AFTERNOON.equals(leave.part()))) {
            throw invalid(entry, "a leave needs type, status and reason; part is MORNING or AFTERNOON on a single day");
        }
        List<ScheduledDay> span = calendar.span(leave.workDaysAgo(), count);
        ScheduledDay first = span.getFirst();
        Instant startAt = first.startAt();
        Instant endAt = span.getLast().endAt();
        int minutes = span.stream().mapToInt(ScheduledDay::minutes).sum();
        Instant workedFrom = null;
        Instant workedTo = null;
        if (leave.part() != null) {
            Instant breakStart = breakStart(entry, first);
            Instant breakEnd = breakStart.plus(Duration.ofMinutes(first.shift().getBreakMinutes()));
            boolean morning = MORNING.equals(leave.part());
            startAt = morning ? first.startAt() : breakEnd;
            endAt = morning ? breakStart : first.endAt();
            workedFrom = morning ? breakEnd : first.startAt();
            workedTo = morning ? first.endAt() : breakStart;
            minutes = (int) Duration.between(startAt, endAt).toMinutes();
        }

        Instant now = Instant.now();
        Instant submittedAt = earliest(leave.type() == LeaveType.SICK
            ? first.workDate().atTime(LocalTime.of(7, 30)).atZone(ClockConfig.BUSINESS_ZONE).toInstant()
            : first.workDate().minusDays(7).atTime(LocalTime.of(9, 0)).atZone(ClockConfig.BUSINESS_ZONE).toInstant(), now.minus(Duration.ofHours(1)));
        Instant decidedAt = earliest(submittedAt.plus(Duration.ofHours(3)), now.minus(Duration.ofMinutes(30)));
        Account approver = approverOn.apply(LocalDate.ofInstant(submittedAt, ClockConfig.BUSINESS_ZONE));
        boolean draft = leave.status() == LeaveRequestStatus.DRAFT;
        boolean reviewed = leave.status() == LeaveRequestStatus.APPROVED || leave.status() == LeaveRequestStatus.REJECTED;
        if (leave.status() == LeaveRequestStatus.REJECTED && leave.comment() == null) {
            throw invalid(entry, "a rejected leave needs a comment");
        }

        LeaveRequest request = LeaveRequest.builder()
            .employee(employee)
            .leaveType(leave.type())
            .salaryTreatment(leave.type().defaultSalaryTreatment())
            .startAt(startAt)
            .endAt(endAt)
            .requestedMinutes(minutes)
            .reason(leave.reason())
            .status(leave.status())
            .submittedAt(draft ? null : submittedAt)
            .approverEmployee(draft ? null : approver.getEmployee())
            .reviewedByAccount(reviewed ? approver : null)
            .reviewComment(reviewed ? leave.comment() : null)
            .reviewedAt(reviewed ? decidedAt : null)
            .build();
        if (leave.status() == LeaveRequestStatus.APPROVED || leave.status() == LeaveRequestStatus.PENDING) {
            leaveEntitlementService.requireSufficientBalance(request);
        }
        leaveRequestRepository.save(request);
        seedTimestamps.set(request, submittedAt.minus(Duration.ofMinutes(10)),
            draft ? submittedAt.minus(Duration.ofMinutes(10)) : leave.status() == LeaveRequestStatus.PENDING ? submittedAt : decidedAt);
        if (!draft) {
            notify(employee, request, approver, submittedAt, decidedAt);
        }
        if (leave.status() != LeaveRequestStatus.APPROVED) {
            return;
        }

        // Days already written as worked make way for the leave, as if the employee had stayed home.
        for (ScheduledDay day : span) {
            attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(employee.getId(), day.workDate()).ifPresent(record -> {
                clear(record);
                record.setStatus(AttendanceStatus.MISSING_PUNCH);
            });
        }
        if (workedFrom != null) {
            AttendanceRecord record = attendanceRecordRepository
                .findByEmployeeIdAndWorkDateForUpdate(employee.getId(), first.workDate()).orElse(null);
            if (record != null) {
                record.setCheckInAt(workedFrom);
                record.setCheckOutAt(workedTo);
                record.setStatus(AttendanceStatus.PRESENT);
            }
        }
        attendanceService.applyApprovedLeave(request);
        for (AttendanceRecord record : attendanceRecordRepository.findByEmployeeIdAndWorkDateBetween(
            employee.getId(), first.workDate(), span.getLast().workDate())) {
            Instant createdAt = seedTimestamps.createdAt(record);
            seedTimestamps.set(record, null, decidedAt.isAfter(createdAt) ? decidedAt : createdAt);
        }
    }

    /** The notifications NotificationService would have sent for a submitted request, at the time of each step. */
    private void notify(Employee employee, LeaveRequest request, Account approver, Instant submittedAt, Instant decidedAt) {
        Account applicant = accountRepository.findByEmployeeId(employee.getId())
            .orElseThrow(() -> new IllegalStateException("Staff seed: " + employee.getEmployeeCode() + " has no account"));
        Map<Long, Account> recipients = new LinkedHashMap<>();
        recipients.put(approver.getId(), approver);
        for (Account hr : accountRoleAssignmentRepository.findActiveAccountsHoldingRole(
            "HR_MANAGER", LocalDate.ofInstant(submittedAt, ClockConfig.BUSINESS_ZONE))) {
            if (employeeAccessScopeService.canAccountAccessEmployee(hr.getId(), employee.getId(), "request.read")) {
                recipients.put(hr.getId(), hr);
            }
        }
        recipients.remove(applicant.getId());

        // Each notification with the time it was sent.
        Map<Notification, Instant> notifications = new LinkedHashMap<>();
        for (Account recipient : recipients.values()) {
            notifications.put(notification(recipient, request, "LEAVE_SUBMITTED", submittedAt), submittedAt);
        }
        if (request.getStatus() == LeaveRequestStatus.APPROVED || request.getStatus() == LeaveRequestStatus.REJECTED) {
            String type = request.getStatus() == LeaveRequestStatus.APPROVED ? "LEAVE_APPROVED" : "LEAVE_REJECTED";
            notifications.put(notification(applicant, request, type, decidedAt), decidedAt);
        }
        notificationRepository.saveAll(notifications.keySet());
        notifications.forEach(seedTimestamps::at);
    }

    private static Notification notification(Account recipient, LeaveRequest request, String type, Instant createdAt) {
        boolean read = createdAt.isBefore(Instant.now().minus(Duration.ofDays(READ_AFTER_DAYS)));
        return Notification.builder()
            .recipient(recipient)
            .leaveRequest(request)
            .type(type)
            .readAt(read ? createdAt.plus(Duration.ofHours(1)) : null)
            .build();
    }

    private AttendanceRecord recordOn(Employee employee, LocalDate date) {
        return attendanceRecordRepository.findByEmployeeIdAndWorkDateForUpdate(employee.getId(), date)
            .orElseThrow(() -> new IllegalStateException("Staff seed: no attendance on " + date + " to change"));
    }

    /** Empties a written day: no punches, no minutes and no overtime. */
    private static void clear(AttendanceRecord record) {
        record.setCheckInAt(null);
        record.setCheckOutAt(null);
        record.setWorkedMinutes(0);
        record.setPayableMinutes(0);
        record.setLateMinutes(0);
        record.setEarlyLeaveMinutes(0);
        record.setOvertimeMinutes(0);
        record.setOvertimeMultiplier(BigDecimal.ONE);
        record.setOvertimeTaxExempt(false);
        record.setOvertimeApprovedByAccount(null);
        record.setOvertimeApprovedAt(null);
    }

    /** Start of the shift break on {@code day}, as AttendanceService places it. */
    private static Instant breakStart(StaffEntry entry, ScheduledDay day) {
        WorkShift shift = day.shift();
        if (shift.getBreakStartTime() == null) {
            throw invalid(entry, "a half-day leave needs a shift with a break");
        }
        long offset = Math.floorMod(Duration.between(shift.getStartTime(), shift.getBreakStartTime()).toMinutes(), 24L * 60L);
        return day.startAt().plus(Duration.ofMinutes(offset));
    }

    private static Instant earliest(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static IllegalStateException invalid(StaffEntry entry, String message) {
        return new IllegalStateException("Staff seed '" + entry.username() + "': " + message);
    }

    /** Work days from the hire date to {@value #FUTURE_DAYS} days ahead, today left out; each day used once. */
    private record Calendar(StaffEntry entry, List<ScheduledDay> days, int pastCount, Set<LocalDate> taken) {

        ScheduledDay pastDay(int workDaysAgo, String field) {
            if (workDaysAgo < 1) {
                throw invalid(entry, field + " must be in the past (workDaysAgo >= 1)");
            }
            return span(workDaysAgo, 1).getFirst();
        }

        List<ScheduledDay> span(int workDaysAgo, int count) {
            int index = workDaysAgo > 0 ? pastCount - workDaysAgo : pastCount - workDaysAgo - 1;
            if (workDaysAgo == 0 || index < 0 || index + count > days.size()) {
                throw invalid(entry, "workDaysAgo " + workDaysAgo + " is outside the employment or the next "
                    + FUTURE_DAYS + " days");
            }
            List<ScheduledDay> span = days.subList(index, index + count);
            for (ScheduledDay day : span) {
                if (!taken.add(day.workDate())) {
                    throw invalid(entry, "two leaves or absences fall on " + day.workDate());
                }
            }
            return span;
        }
    }
}
