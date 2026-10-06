package com.htttdn.hrm.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ConflictException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.repository.CompanyHolidayRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;

@Service
@Transactional
public class CompanyHolidayService {
    private final CompanyHolidayRepository holidayRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final EmployeeAccessScopeService accessScopeService;

    public CompanyHolidayService(CompanyHolidayRepository holidayRepository,
        PayrollPeriodRepository payrollPeriodRepository, EmployeeAccessScopeService accessScopeService) {
        this.holidayRepository = holidayRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.accessScopeService = accessScopeService;
    }

    @PreAuthorize("hasAuthority('organization.read')")
    @Transactional(readOnly = true)
    public List<CompanyHoliday> list(LocalDate from, LocalDate to) {
        accessScopeService.requireCompanyWide("organization.read");
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to);
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public CompanyHoliday create(LocalDate date, String name) {
        accessScopeService.requireCompanyWide("organization.manage");
        invalidateCalculatedPeriodOrReject(date);
        if (holidayRepository.findByHolidayDate(date).isPresent()) {
            throw new ConflictException(ErrorCode.CONFLICT, "Holiday already exists on " + date);
        }
        return holidayRepository.save(CompanyHoliday.builder()
            .holidayDate(date).name(name.trim()).createdAt(Instant.now()).build());
    }

    @PreAuthorize("hasAuthority('organization.manage')")
    public void delete(Long id) {
        accessScopeService.requireCompanyWide("organization.manage");
        CompanyHoliday holiday = holidayRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Holiday not found: " + id));
        invalidateCalculatedPeriodOrReject(holiday.getHolidayDate());
        holidayRepository.delete(holiday);
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
                    period.setUpdatedAt(Instant.now());
                }
            });
    }
}
