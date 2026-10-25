package com.htttdn.hrm.config.seed;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.config.seed.StaffSeeder.ResignationEntry;
import com.htttdn.hrm.config.seed.StaffSeeder.StaffEntry;
import com.htttdn.hrm.config.seed.StaffSeeder.TerminationEntry;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.entity.ResignationRequest;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.ResignationRequestStatus;
import com.htttdn.hrm.repository.ResignationRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ghi đơn xin nghỉ việc của một nhân viên được seed, như nhân viên gửi và người duyệt (quản lý trực
 * tiếp, không có thì Giám đốc) quyết định.
 *
 * <p>Người đã nghỉ việc theo {@code termination} với trạng thái {@code RESIGNED} có một đơn đã duyệt
 * và đã hoàn tất: gửi trước ngày làm việc cuối cùng 30 ngày, duyệt hôm sau, hoàn tất đầu ngày kế tiếp
 * ngày làm việc cuối như {@code ResignationCompletionJob}.
 *
 * <p>{@code resignations} là đơn của người còn đang làm: {@code daysAgo} là ngày gửi,
 * {@code lastWorkingDaysAgo} là ngày làm việc cuối đề nghị, số âm là ngày sắp tới. Đơn còn mở (chờ
 * duyệt, hoặc đã duyệt) phải có ngày làm việc cuối từ hôm nay trở đi, và mỗi người chỉ có một đơn mở.
 */
@Component
@RequiredArgsConstructor
class SeedResignationWriter {

    private static final int NOTICE_DAYS = 30;

    private final ResignationRequestRepository resignationRequestRepository;
    private final SeedTimestamps seedTimestamps;

    /** @param approverOn the account that decides the employee's requests on a date */
    void write(StaffEntry entry, Employee employee, Function<LocalDate, Account> approverOn) {
        TerminationEntry termination = entry.termination();
        if (termination != null && termination.status() == EmploymentStatus.RESIGNED) {
            LocalDate lastWorkingDate = employee.getTerminationDate();
            LocalDate submittedOn = lastWorkingDate.minusDays(NOTICE_DAYS).isBefore(employee.getHireDate())
                ? employee.getHireDate() : lastWorkingDate.minusDays(NOTICE_DAYS);
            Instant submittedAt = submittedOn.atTime(LocalTime.of(9, 0)).atZone(ClockConfig.BUSINESS_ZONE).toInstant();
            Instant decidedAt = submittedAt.plus(Duration.ofDays(1));
            Instant completedAt = lastWorkingDate.plusDays(1).atTime(LocalTime.of(0, 10)).atZone(ClockConfig.BUSINESS_ZONE).toInstant();
            Account approver = approverOn.apply(submittedOn);
            seedTimestamps.set(resignationRequestRepository.save(ResignationRequest.builder()
                .employee(employee)
                .lastWorkingDate(lastWorkingDate)
                .reason(termination.reason())
                .status(ResignationRequestStatus.APPROVED)
                .approverEmployee(approver.getEmployee())
                .reviewedByAccount(approver)
                .reviewComment(termination.comment())
                .reviewedAt(decidedAt)
                .completedAt(completedAt)
                .build()), submittedAt, completedAt);
        }

        if (entry.resignations() == null) {
            return;
        }
        if (termination != null) {
            throw invalid(entry, "resignations are for staff still employed; a resignation that ended the"
                + " employment comes from termination");
        }
        LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
        Instant now = Instant.now();
        boolean open = false;
        for (ResignationEntry item : entry.resignations()) {
            ResignationRequestStatus status = item.status();
            LocalDate lastWorkingDate = today.minusDays(item.lastWorkingDaysAgo());
            LocalDate submittedOn = today.minusDays(item.daysAgo());
            boolean opens = status == ResignationRequestStatus.PENDING || status == ResignationRequestStatus.APPROVED;
            if (status == null || item.reason() == null || item.daysAgo() < 0
                || submittedOn.isBefore(employee.getHireDate()) || lastWorkingDate.isBefore(submittedOn)
                || status == ResignationRequestStatus.REJECTED && item.comment() == null
                || opens && (lastWorkingDate.isBefore(today) || open)) {
                throw invalid(entry, "a resignation needs a status and reason, is sent after the hire date and"
                    + " before its last working day; a rejected one needs a comment; only one stays open,"
                    + " with its last working day today or later");
            }
            open = open || opens;

            Instant submittedAt = submittedOn.atTime(LocalTime.of(9, 0)).atZone(ClockConfig.BUSINESS_ZONE).toInstant();
            Instant decidedAt = submittedAt.plus(Duration.ofDays(1));
            if (decidedAt.isAfter(now)) {
                decidedAt = now.minus(Duration.ofMinutes(30));
            }
            Account approver = approverOn.apply(submittedOn);
            boolean reviewed = status == ResignationRequestStatus.APPROVED || status == ResignationRequestStatus.REJECTED;
            seedTimestamps.set(resignationRequestRepository.save(ResignationRequest.builder()
                .employee(employee)
                .lastWorkingDate(lastWorkingDate)
                .reason(item.reason())
                .status(status)
                .approverEmployee(approver.getEmployee())
                .reviewedByAccount(reviewed ? approver : null)
                .reviewComment(reviewed ? item.comment() : null)
                .reviewedAt(reviewed ? decidedAt : null)
                .build()), submittedAt, status == ResignationRequestStatus.PENDING ? submittedAt : decidedAt);
        }
    }

    private static IllegalStateException invalid(StaffEntry entry, String message) {
        return new IllegalStateException("Staff seed '" + entry.username() + "': " + message);
    }
}
