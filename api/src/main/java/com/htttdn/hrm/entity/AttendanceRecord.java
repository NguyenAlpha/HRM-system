package com.htttdn.hrm.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.AttendanceStatus;

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
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private WorkShift shift;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "worked_minutes", nullable = false)
    private Integer workedMinutes;

    @Column(name = "payable_minutes", nullable = false)
    private Integer payableMinutes;

    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes;

    @Column(name = "early_leave_minutes", nullable = false)
    private Integer earlyLeaveMinutes;

    @Column(name = "overtime_minutes", nullable = false)
    private Integer overtimeMinutes;

    @Column(name = "overtime_multiplier", nullable = false, precision = 8, scale = 4)
    private BigDecimal overtimeMultiplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "overtime_approved_by_account_id")
    private Account overtimeApprovedByAccount;

    @Column(name = "overtime_approved_at")
    private Instant overtimeApprovedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_account_id")
    private Account updatedByAccount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
