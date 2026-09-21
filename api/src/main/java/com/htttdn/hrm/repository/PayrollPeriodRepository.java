package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.PayrollPeriod;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {

    Optional<PayrollPeriod> findByYearAndMonth(Short year, Short month);

    Optional<PayrollPeriod> findFirstByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
        LocalDate periodStart, LocalDate periodEnd);
}
