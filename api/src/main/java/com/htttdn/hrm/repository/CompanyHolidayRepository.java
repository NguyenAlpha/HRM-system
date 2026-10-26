package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.CompanyHoliday;
import com.htttdn.hrm.exception.ResourceNotFoundException;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {
    List<CompanyHoliday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);
    Optional<CompanyHoliday> findByHolidayDate(LocalDate date);

    /** Ngày lễ theo id, không có thì 404 {@code RESOURCE_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default CompanyHoliday getRequired(Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Holiday not found: " + id);
    }
}
