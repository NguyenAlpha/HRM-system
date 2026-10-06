package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.CompanyHoliday;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {
    List<CompanyHoliday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);
    Optional<CompanyHoliday> findByHolidayDate(LocalDate date);
}
