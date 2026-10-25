package com.htttdn.hrm.config.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.repository.CompanyHolidayRepository;

@ExtendWith(MockitoExtension.class)
class CompanyHolidaySeederTest {

    @Mock private CompanyHolidayRepository companyHolidayRepository;

    @Test
    void everyYearHasTheElevenStatutoryDaysOnWorkingDaysOfThatYear() {
        CompanyHolidaySeeder.HOLIDAYS.forEach((year, holidays) -> {
            assertEquals(11, holidays.size(), "holidays in " + year);
            holidays.forEach(holiday -> {
                assertEquals(year, holiday.date().getYear());
                // A holiday on Sunday is moved to the next working day, so none may be a Sunday.
                assertNotEquals(DayOfWeek.SUNDAY, holiday.date().getDayOfWeek(), holiday.name());
            });
        });
    }

    @Test
    void aYearThatAlreadyHasHolidaysIsLeftAsHrMadeIt() {
        when(companyHolidayRepository.findByHolidayDateBetweenOrderByHolidayDate(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
            .thenReturn(List.of(CompanyHoliday.builder().holidayDate(LocalDate.of(2025, 1, 1)).build()));
        when(companyHolidayRepository.findByHolidayDateBetweenOrderByHolidayDate(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).thenReturn(List.of());

        new CompanyHolidaySeeder(companyHolidayRepository, new CompanySeedProperties(true)).run(null);

        verify(companyHolidayRepository, times(CompanyHolidaySeeder.HOLIDAYS.get(2026).size()))
            .save(any(CompanyHoliday.class));
    }
}
