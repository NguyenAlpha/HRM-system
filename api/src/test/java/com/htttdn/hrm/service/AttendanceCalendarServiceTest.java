package com.htttdn.hrm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.entity.EmployeeAssignment;
import com.htttdn.hrm.entity.WorkShift;
import com.htttdn.hrm.repository.CompanyHolidayRepository;
import com.htttdn.hrm.repository.EmployeeAssignmentRepository;

@ExtendWith(MockitoExtension.class)
class AttendanceCalendarServiceTest {
    @Mock private EmployeeAssignmentRepository assignmentRepository;
    @Mock private CompanyHolidayRepository holidayRepository;

    @Test
    void schedulesMondayThroughSaturdayExceptCompanyHolidays() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 4);
        WorkShift shift = WorkShift.builder().startTime(LocalTime.of(22, 0))
            .endTime(LocalTime.of(6, 0)).crossesMidnight(true)
            .standardWorkMinutes(420).build();
        EmployeeAssignment assignment = EmployeeAssignment.builder().isPrimary(true)
            .effectiveFrom(from).shift(shift).build();
        when(assignmentRepository.findByEmployeeIdOrderByEffectiveFromDesc(2L))
            .thenReturn(List.of(assignment));
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to))
            .thenReturn(List.of(CompanyHoliday.builder().holidayDate(LocalDate.of(2026, 1, 2)).build()));

        var days = new AttendanceCalendarService(assignmentRepository, holidayRepository)
            .scheduleFor(2L, from, to);

        assertEquals(List.of(from, LocalDate.of(2026, 1, 3)),
            days.stream().map(AttendanceCalendarService.ScheduledDay::workDate).toList());
        assertEquals(LocalDate.of(2026, 1, 2),
            LocalDate.ofInstant(days.get(0).endAt(), java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
    }
}
