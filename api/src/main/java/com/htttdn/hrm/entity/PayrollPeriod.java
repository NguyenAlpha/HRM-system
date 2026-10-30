package com.htttdn.hrm.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.PayrollPeriodStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payroll_periods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriod extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Short year;

    @Column(nullable = false)
    private Short month;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "tax_payment_date")
    private LocalDate taxPaymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollPeriodStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculated_by_account_id")
    private Account calculatedByAccount;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_account_id")
    private Account approvedByAccount;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_account_id")
    private Account paidByAccount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by_account_id")
    private Account lockedByAccount;

    @Column(name = "locked_at")
    private Instant lockedAt;

    /** Lần hủy duyệt gần nhất: người, thời điểm và lý do (cùng có hoặc cùng trống). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by_account_id")
    private Account reopenedByAccount;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;
}
