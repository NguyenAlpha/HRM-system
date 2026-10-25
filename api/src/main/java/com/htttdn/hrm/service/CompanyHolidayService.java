package com.htttdn.hrm.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.attendance.CompanyHolidayResponse;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.CompanyHolidayRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyHolidayService {
    private final CompanyHolidayRepository holidayRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final EmployeeAccessScopeService accessScopeService;

    /** The holiday calendar is the same for the whole company, so reading it has no scope. */
    @PreAuthorize("hasAuthority('holiday.read')")
    @Transactional(readOnly = true)
    public List<CompanyHolidayResponse> list(LocalDate from, LocalDate to) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to).stream()
            .map(this::toResponse).toList();
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public CompanyHolidayResponse create(LocalDate date, String name) {
        accessScopeService.requireCompanyWide("organization.manage");
        invalidateCalculatedPeriodOrReject(date);
        if (holidayRepository.findByHolidayDate(date).isPresent()) {
            throw new ConflictException(ErrorCode.CONFLICT, "Holiday already exists on " + date);
        }
        return toResponse(holidayRepository.save(CompanyHoliday.builder()
            .holidayDate(date).name(name.trim()).build()));
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public void delete(Long id) {
        accessScopeService.requireCompanyWide("organization.manage");
        CompanyHoliday holiday = holidayRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Holiday not found: " + id));
        invalidateCalculatedPeriodOrReject(holiday.getHolidayDate());
        holidayRepository.delete(holiday);
    }

    private CompanyHolidayResponse toResponse(CompanyHoliday holiday) {
        return new CompanyHolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getName(),
            holiday.getCreatedAt());
    }

    private void invalidateCalculatedPeriodOrReject(LocalDate date) {
        payrollPeriodRepository.findContainingDateForUpdate(date)
            .ifPresent(period -> {
                if (period.getStatus() == PayrollPeriodStatus.APPROVED
                    || period.getStatus() == PayrollPeriodStatus.PAID
                    || period.getStatus() == PayrollPeriodStatus.LOCKED) {
                    throw new ConflictException(ErrorCode.PAYROLL_PERIOD_LOCKED,
                        "Holiday belongs to approved payroll period " + period.getId());
                }
                if (period.getStatus() == PayrollPeriodStatus.CALCULATED) {
                    period.setStatus(PayrollPeriodStatus.DRAFT);
                    period.setCalculatedByAccount(null);
                    period.setCalculatedAt(null);
                }
            });
    }
}
