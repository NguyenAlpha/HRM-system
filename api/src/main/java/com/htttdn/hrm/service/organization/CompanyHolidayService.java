package com.htttdn.hrm.service.organization;

import java.time.LocalDate;
import java.util.List;

import com.htttdn.hrm.service.rbac.EmployeeAccessScopeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.attendance.CompanyHolidayResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.repository.AttendanceRecordRepository;
import com.htttdn.hrm.repository.CompanyHolidayRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.service.payroll.PayrollPeriodInvalidator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyHolidayService {
    private final CompanyHolidayRepository holidayRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final EmployeeAccessScopeService accessScopeService;
    private final PayrollPeriodInvalidator payrollPeriodInvalidator;
    private final AttendanceRecordRepository attendanceRecordRepository;

    /** The holiday calendar is the same for the whole company, so reading it has no scope. */
    @PreAuthorize("hasAuthority('holiday.read')")
    @Transactional(readOnly = true)
    public List<CompanyHolidayResponse> list(LocalDate from, LocalDate to) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to).stream()
            .map(this::toResponse).toList();
    }

    /**
     * Declares a holiday. Prepared days of that date with nothing recorded yet are deleted, since the
     * day is no longer a work day.
     *
     * @throws ConflictException the date is already a holiday (→ 409 {@code HOLIDAY_DATE_TAKEN}), someone's
     *         day on that date already has something recorded (→ 409 {@code HOLIDAY_DAY_RECORDED}), or the
     *         payroll period holding it is approved, paid or locked (→ 409 {@code PAYROLL_PERIOD_LOCKED})
     */
    @PreAuthorize("hasAuthority('organization.manage')")
    public CompanyHolidayResponse create(LocalDate date, String name) {
        accessScopeService.requireCompanyWide("organization.manage");
        if (holidayRepository.findByHolidayDate(date).isPresent()) {
            throw dateTaken(date);
        }
        requireNothingRecorded(date);
        invalidateCalculatedPeriodOrReject(date);
        attendanceRecordRepository.deleteUntouchedOn(date);
        try {
            return toResponse(holidayRepository.saveAndFlush(CompanyHoliday.builder()
                .holidayDate(date).name(name.trim()).build()));
        } catch (DataIntegrityViolationException exception) {
            throw dateTaken(date);
        }
    }

    /**
     * Removes a holiday. The date becomes a work day again; its days are prepared by the next
     * {@code POST /api/attendance/prepare} (or the nightly job for the current month).
     *
     * @throws ConflictException someone's day on that date already has something recorded
     *         (→ 409 {@code HOLIDAY_DAY_RECORDED}), or the payroll period holding it is approved, paid or
     *         locked (→ 409 {@code PAYROLL_PERIOD_LOCKED})
     */
    @PreAuthorize("hasAuthority('organization.manage')")
    public void delete(Long id) {
        accessScopeService.requireCompanyWide("organization.manage");
        CompanyHoliday holiday = holidayRepository.getRequired(id);
        requireNothingRecorded(holiday.getHolidayDate());
        invalidateCalculatedPeriodOrReject(holiday.getHolidayDate());
        holidayRepository.delete(holiday);
    }

    /**
     * Payable minutes are worked out when a day is recorded, by whether it was a work day then, so a
     * recorded day would keep the wrong minutes once the date turns into a holiday or back.
     */
    private void requireNothingRecorded(LocalDate date) {
        if (attendanceRecordRepository.existsRecordedOn(date)) {
            throw new ConflictException(ErrorCode.HOLIDAY_DAY_RECORDED,
                "Attendance is already recorded on " + date, "date");
        }
    }

    private static ConflictException dateTaken(LocalDate date) {
        return new ConflictException(ErrorCode.HOLIDAY_DATE_TAKEN, "Holiday already exists on " + date, "date");
    }

    private CompanyHolidayResponse toResponse(CompanyHoliday holiday) {
        return new CompanyHolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getName(),
            holiday.getCreatedAt());
    }

    private void invalidateCalculatedPeriodOrReject(LocalDate date) {
        payrollPeriodRepository.findContainingDateForUpdate(date)
            .ifPresent(payrollPeriodInvalidator::invalidatePeriod);
    }
}
