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

    @Query("""
        SELECT CASE WHEN COUNT(period) > 0 THEN true ELSE false END FROM PayrollPeriod period
        WHERE period.id <> :periodId AND period.taxPaymentDate BETWEEN :from AND :to
          AND period.status IN :statuses
        """)
    boolean existsOtherInPaymentMonth(@Param("periodId") Long periodId,
        @Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("statuses") List<PayrollPeriodStatus> statuses);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT period FROM PayrollPeriod period
        WHERE period.taxPaymentDate >= :from
          AND (:to IS NULL OR period.taxPaymentDate <= :to)
          AND period.status IN :statuses
        ORDER BY period.taxPaymentDate, period.id
        """)
    List<PayrollPeriod> findAffectedPaymentPeriodsForUpdate(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("statuses") List<PayrollPeriodStatus> statuses);
}
