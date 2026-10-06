package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.entity.PayrollPeriod;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;

import jakarta.persistence.LockModeType;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {

    Optional<PayrollPeriod> findByYearAndMonth(Short year, Short month);

    Optional<PayrollPeriod> findFirstByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
        LocalDate periodStart, LocalDate periodEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT period FROM PayrollPeriod period WHERE period.id = :id")
    Optional<PayrollPeriod> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT period FROM PayrollPeriod period
        WHERE period.periodStart <= :date AND period.periodEnd >= :date
        """)
    Optional<PayrollPeriod> findContainingDateForUpdate(@Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT period FROM PayrollPeriod period
        WHERE period.periodEnd >= :date AND period.status IN :statuses
        ORDER BY period.periodStart, period.id
        """)
    List<PayrollPeriod> findAffectedPeriodsForUpdate(
        @Param("date") LocalDate date,
        @Param("statuses") List<PayrollPeriodStatus> statuses);
}
