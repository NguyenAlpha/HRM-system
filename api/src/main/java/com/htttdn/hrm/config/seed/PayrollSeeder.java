package com.htttdn.hrm.config.seed;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.payroll.CreatePayrollAdjustmentRequest;
import com.htttdn.hrm.dto.request.payroll.CreatePayrollPeriodRequest;
import com.htttdn.hrm.dto.request.payroll.PayrollActionRequest;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.enums.PayrollAdjustmentType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PayrollPeriodRepository;
import com.htttdn.hrm.service.PayrollAdjustmentService;
import com.htttdn.hrm.service.PayrollService;

import jakarta.persistence.EntityManager;

/**
 * Khởi tạo kỳ lương mọi tháng từ tháng nhân viên đầu tiên vào làm đến tháng trước tháng chạy seed,
 * qua đúng engine tính lương:
 * kế toán lương (role {@code PAYROLL_ACCOUNTANT}) tạo kỳ, nhập khoản điều chỉnh và tính; Company Owner
 * (giữ {@code PAYROLL_APPROVER}) duyệt, xác nhận đã trả và khóa.
 *
 * <p>Tháng trước mới tính xong, chờ duyệt; tháng trước nữa đã trả, chưa khóa; các tháng còn lại đã
 * khóa. Lương tháng M được tính đầu tháng M+1 và trả ngày 10. Mốc thời gian được lùi về các ngày đó.
 * Kỳ đã duyệt khóa chấm công của tháng, như khi dùng thật.
 *
 * <p>Chỉ chạy khi chưa có kỳ lương nào, cần kế toán lương và người duyệt lương; thiếu thì bỏ qua.
 * Chạy sau nhân sự seed (order 520).
 */
@Component
@Order(520)
public class PayrollSeeder implements ApplicationRunner {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> CALCULATOR_AUTHORITIES = List.of("payroll.calculate");
    private static final List<String> APPROVER_AUTHORITIES = List.of("payroll.approve", "payroll.mark_paid", "payroll.lock");

    private static final Logger log = LoggerFactory.getLogger(PayrollSeeder.class);

    /** A manual payroll line entered by the accountant in the period {@code monthsAgo} months back. */
    private record Adjustment(int monthsAgo, String username, PayrollAdjustmentType type, String description, long amount) {
    }

    private static final List<Adjustment> ADJUSTMENTS = List.of(
        new Adjustment(5, "hr", PayrollAdjustmentType.BONUS, "Thưởng hoàn thành kế hoạch tuyển dụng quý", 3_000_000),
        new Adjustment(4, "employee", PayrollAdjustmentType.DEDUCTION, "Hoàn tạm ứng lương", 1_000_000),
        new Adjustment(3, "accountant", PayrollAdjustmentType.NON_TAXABLE_ALLOWANCE, "Trợ cấp khó khăn đột xuất", 2_000_000),
        new Adjustment(2, "employee", PayrollAdjustmentType.TAXABLE_ALLOWANCE, "Phụ cấp kiêm nhiệm văn thư", 500_000),
        new Adjustment(1, "hr", PayrollAdjustmentType.BONUS, "Thưởng nhân viên xuất sắc tháng", 1_000_000),
        new Adjustment(1, "accountant", PayrollAdjustmentType.DEDUCTION, "Khấu trừ đồng phục", 300_000)
    );

    private final PayrollService payrollService;
    private final PayrollAdjustmentService payrollAdjustmentService;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public PayrollSeeder(
        PayrollService payrollService,
        PayrollAdjustmentService payrollAdjustmentService,
        PayrollPeriodRepository payrollPeriodRepository,
        AccountRepository accountRepository,
        AccountRoleAssignmentRepository accountRoleAssignmentRepository,
        EntityManager entityManager,
        JdbcTemplate jdbc,
        @Value("${payroll.seed.enabled:true}") boolean enabled
    ) {
        this.payrollService = payrollService;
        this.payrollAdjustmentService = payrollAdjustmentService;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.accountRepository = accountRepository;
        this.accountRoleAssignmentRepository = accountRoleAssignmentRepository;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || payrollPeriodRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        Optional<Account> calculator = holder("PAYROLL_ACCOUNTANT", today);
        Optional<Account> approver = holder("PAYROLL_APPROVER", today);
        if (calculator.isEmpty() || approver.isEmpty()) {
            log.info("Payroll seed skipped: needs a payroll accountant and a payroll approver");
            return;
        }

        YearMonth currentMonth = YearMonth.from(today);
        LocalDate firstHire = jdbc.queryForObject("SELECT min(hire_date) FROM employees WHERE deleted_at IS NULL", LocalDate.class);
        YearMonth firstMonth = YearMonth.from(firstHire);
        int months = (int) firstMonth.until(currentMonth, ChronoUnit.MONTHS);
        try {
            for (int monthsAgo = months; monthsAgo >= 1; monthsAgo--) {
                seedPeriod(currentMonth.minusMonths(monthsAgo), monthsAgo, calculator.get(), approver.get());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        log.info("Payroll seed completed: {} periods from {}", months, firstMonth);
    }

    private void seedPeriod(YearMonth month, int monthsAgo, Account calculator, Account approver) {
        YearMonth paymentMonth = month.plusMonths(1);
        LocalDate taxPaymentDate = paymentMonth.atDay(10);

        authenticate(calculator, CALCULATOR_AUTHORITIES);
        Long periodId = payrollService.createPeriod(new CreatePayrollPeriodRequest(
            (short) month.getYear(), (short) month.getMonthValue(), taxPaymentDate)).id();
        for (Adjustment adjustment : ADJUSTMENTS) {
            if (adjustment.monthsAgo() == monthsAgo) {
                Long employeeId = accountRepository.findByUsername(adjustment.username())
                    .map(account -> account.getEmployee().getId())
                    .orElseThrow(() -> new IllegalStateException("Payroll seed: no account '" + adjustment.username() + "'"));
                payrollAdjustmentService.create(periodId, new CreatePayrollAdjustmentRequest(
                    employeeId, adjustment.type(), adjustment.description(), BigDecimal.valueOf(adjustment.amount())));
            }
        }
        payrollService.calculate(periodId, new PayrollActionRequest());
        if (monthsAgo >= 2) {
            authenticate(approver, APPROVER_AUTHORITIES);
            payrollService.approve(periodId, new PayrollActionRequest());
            payrollService.markPaid(periodId, new PayrollActionRequest(taxPaymentDate));
            if (monthsAgo >= 3) {
                payrollService.lock(periodId, new PayrollActionRequest());
            }
        }
        backdate(periodId, paymentMonth, monthsAgo);
    }

    /** Moves the period's timestamps to when that payroll was run: created on the 1st, calculated on the 2nd, paid on the 10th. */
    private void backdate(Long periodId, YearMonth paymentMonth, int monthsAgo) {
        entityManager.flush();
        Timestamp created = at(paymentMonth.atDay(1), 8);
        Timestamp calculated = at(paymentMonth.atDay(2), 9);
        Timestamp approved = monthsAgo >= 2 ? at(paymentMonth.atDay(4), 14) : null;
        Timestamp paid = monthsAgo >= 2 ? at(paymentMonth.atDay(10), 10) : null;
        Timestamp locked = monthsAgo >= 3 ? at(paymentMonth.atDay(20), 16) : null;
        Timestamp updated = locked != null ? locked : paid != null ? paid : calculated;
        jdbc.update("""
            UPDATE payroll_periods SET created_at = ?, calculated_at = ?, approved_at = ?, paid_at = ?, locked_at = ?,
                updated_at = ?
            WHERE id = ?
            """, created, calculated, approved, paid, locked, updated, periodId);
        jdbc.update("UPDATE payroll_adjustments SET created_at = ? WHERE payroll_period_id = ?",
            at(paymentMonth.atDay(1), 10), periodId);
        jdbc.update("UPDATE payslips SET created_at = ?, updated_at = ? WHERE payroll_period_id = ?",
            calculated, calculated, periodId);
        jdbc.update("""
            UPDATE payslip_items SET created_at = ?
            WHERE payslip_id IN (SELECT id FROM payslips WHERE payroll_period_id = ?)
            """, calculated, periodId);
        entityManager.clear();
    }

    /** {@code date} at {@code hour}, or an hour ago when that is still ahead (the 1st or 2nd of the current month). */
    private static Timestamp at(LocalDate date, int hour) {
        Instant instant = date.atTime(LocalTime.of(hour, 0)).atZone(ZONE).toInstant();
        Instant latest = Instant.now().minusSeconds(3600);
        return Timestamp.from(instant.isAfter(latest) ? latest : instant);
    }

    private Optional<Account> holder(String roleCode, LocalDate date) {
        return accountRoleAssignmentRepository.findAccountsHoldingRole(roleCode, date).stream().findFirst();
    }

    /** Acts as {@code account} for the payroll services, which check the authority and read the account from the token. */
    private static void authenticate(Account account, List<String> authorities) {
        Jwt jwt = Jwt.withTokenValue("payroll-seed")
            .header("alg", "none")
            .claim("accountId", account.getId())
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            authorities.stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
