package com.htttdn.hrm.repository;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Queries taking an optional end date run on Postgres with and without it. The driver sends a date
 * parameter untyped, so "{@code :to IS NULL}" alone cannot be planned; a mocked repository hides that.
 */
@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "admin.seed.enabled=false",
    "owner.seed.enabled=false",
    "staff.seed.enabled=false",
    "payroll.seed.enabled=false",
    "attendance.auto-prepare.enabled=false",
    "rate-limit.enabled=false"
})
@Transactional
class OptionalDateBoundQueryIntegrationTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    @Autowired private PayrollPeriodRepository payrollPeriodRepository;
    @Autowired private AccountPermissionOverrideRepository permissionOverrideRepository;

    @Test
    void affectedPaymentPeriodsWithAndWithoutAnEnd() {
        List<PayrollPeriodStatus> statuses = List.of(PayrollPeriodStatus.CALCULATED);
        assertDoesNotThrow(() -> payrollPeriodRepository.findAffectedPaymentPeriodsForUpdate(FROM, null, statuses));
        assertDoesNotThrow(() -> payrollPeriodRepository.findAffectedPaymentPeriodsForUpdate(FROM, FROM.plusMonths(1), statuses));
    }

    @Test
    void overlappingPermissionOverrideWithAndWithoutAnEnd() {
        assertDoesNotThrow(() -> permissionOverrideRepository.existsOverlappingActive(-1L, -1L, FROM, null));
        assertDoesNotThrow(() -> permissionOverrideRepository.existsOverlappingActive(-1L, -1L, FROM, FROM.plusMonths(1)));
    }
}
