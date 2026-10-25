package com.htttdn.hrm.service.attendance;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.repository.CompanyHolidayRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AttendanceCalendarService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final EmployeeAssignmentRepository assignmentRepository;
    private final CompanyHolidayRepository holidayRepository;

    public List<ScheduledDay> scheduleFor(Long employeeId, LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to)
            .stream().map(CompanyHoliday::getHolidayDate).collect(Collectors.toSet());
        List<EmployeeAssignment> assignments = assignmentRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
        List<ScheduledDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(date)) {
                continue;
            }
            LocalDate workDate = date;
            EmployeeAssignment assignment = assignments.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsPrimary())
                    && !item.getEffectiveFrom().isAfter(workDate)
                    && (item.getEffectiveTo() == null || !item.getEffectiveTo().isBefore(workDate)))
                .findFirst().orElse(null);
            if (assignment == null) {
                continue;
            }
            days.add(toScheduledDay(employeeId, workDate, assignment));
        }
        return days;
    }

    /**
     * The shift window of the primary assignment on {@code date}, Sundays and company holidays
     * included, so punches on a day off can still be recorded as overtime.
     */
    public Optional<ScheduledDay> assignedDay(Long employeeId, LocalDate date) {
        return assignmentRepository.findCurrentPrimaryCandidates(employeeId, date).stream().findFirst()
            .map(assignment -> toScheduledDay(employeeId, date, assignment));
    }

    private static ScheduledDay toScheduledDay(Long employeeId, LocalDate workDate, EmployeeAssignment assignment) {
        WorkShift shift = assignment.getShift();
        if (shift == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Employee " + employeeId + " has no shift on " + workDate);
        }
        Instant start = LocalDateTime.of(workDate, shift.getStartTime()).atZone(ZONE).toInstant();
        LocalDate endDate = Boolean.TRUE.equals(shift.getCrossesMidnight()) ? workDate.plusDays(1) : workDate;
        Instant end = LocalDateTime.of(endDate, shift.getEndTime()).atZone(ZONE).toInstant();
        return new ScheduledDay(workDate, shift, start, end, shift.getStandardWorkMinutes());
    }

    public long companyWorkdayCount(LocalDate from, LocalDate to) {
        return companyWorkDates(from, to).size();
    }

    public boolean isCompanyHoliday(LocalDate date) {
        return holidayRepository.findByHolidayDate(date).isPresent();
    }

    public List<LocalDate> companyWorkDates(LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to)
            .stream().map(CompanyHoliday::getHolidayDate).collect(Collectors.toSet());
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(date)) {
                dates.add(date);
            }
        }
        return dates;
    }

    public record ScheduledDay(LocalDate workDate, WorkShift shift, Instant startAt, Instant endAt, int minutes) { }
}
