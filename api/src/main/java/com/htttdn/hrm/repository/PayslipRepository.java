package com.htttdn.hrm.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.entity.Payslip;
import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;
import com.htttdn.hrm.exception.ResourceNotFoundException;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    Optional<Payslip> findByPayrollPeriodIdAndEmployeeId(Long payrollPeriodId, Long employeeId);

    List<Payslip> findByPayrollPeriodId(Long payrollPeriodId);

    List<Payslip> findByPayrollPeriodIdOrderByEmployeeCodeSnapshotAsc(Long payrollPeriodId);

    /** Count and money totals of one period's payslips. */
    interface PeriodTotals {
        long getPayslipCount();

        BigDecimal getTotalGrossPay();

        BigDecimal getTotalNetPay();
    }

    @Query("""
        SELECT COUNT(payslip) AS payslipCount,
               COALESCE(SUM(payslip.grossPay), 0) AS totalGrossPay,
               COALESCE(SUM(payslip.netPay), 0) AS totalNetPay
        FROM Payslip payslip
        WHERE payslip.payrollPeriod.id = :periodId
        """)
    PeriodTotals totalsForPeriod(@Param("periodId") Long periodId);

    Page<Payslip> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<Payslip> findByEmployeeIdAndPayrollPeriodStatusIn(Long employeeId,
        Collection<PayrollPeriodStatus> statuses, Pageable pageable);

    boolean existsByEmployeeId(Long employeeId);

    /** Phiếu lương của các kỳ trong năm có trạng thái thuộc {@code statuses}, kèm kỳ lương. */
    @Query("""
        SELECT payslip
        FROM Payslip payslip
        JOIN FETCH payslip.payrollPeriod period
        WHERE period.year = :year
          AND period.status IN :statuses
        """)
    List<Payslip> findForReportYear(@Param("year") short year, @Param("statuses") List<PayrollPeriodStatus> statuses);

    /** Phiếu lương theo id, không có thì 404 {@code PAYSLIP_NOT_FOUND}. Xem {@link AccountRepository#getRequired}. */
    default Payslip getRequired(Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException(ErrorCode.PAYSLIP_NOT_FOUND, "Payslip not found: " + id);
    }
}
