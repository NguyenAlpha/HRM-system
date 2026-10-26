package com.htttdn.hrm.entity;

import java.time.Instant;

import com.htttdn.hrm.entity.enums.LeaveRequestStatus;
import com.htttdn.hrm.entity.enums.LeaveSalaryTreatment;
import com.htttdn.hrm.entity.enums.LeaveType;

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
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 30)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_treatment", nullable = false, length = 30)
    private LeaveSalaryTreatment salaryTreatment;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "requested_minutes", nullable = false)
    private Integer requestedMinutes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveRequestStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Fixed when the request is submitted; null for drafts and self-recorded leave. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_employee_id")
    private Employee approverEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_account_id")
    private Account reviewedByAccount;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Whether {@code account} belongs to the approver fixed when the request was submitted; false before that. */
    public boolean isApprover(Account account) {
        return approverEmployee != null && account.belongsTo(approverEmployee.getId());
    }
}
