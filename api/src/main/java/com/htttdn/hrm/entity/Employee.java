package com.htttdn.hrm.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.EducationLevel;
import com.htttdn.hrm.entity.enums.EmploymentStatus;
import com.htttdn.hrm.entity.enums.Gender;

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
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 30)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "highest_education_level", length = 20)
    private EducationLevel highestEducationLevel;

    @Column(length = 200)
    private String major;

    @Column(length = 200)
    private String institution;

    @Column(name = "graduation_year")
    private Short graduationYear;

    @Column(name = "national_id", unique = true, length = 30)
    private String nationalId;

    @Column(name = "personal_email", length = 100)
    private String personalEmail;

    @Column(name = "work_email", unique = true, length = 100)
    private String workEmail;

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", length = 200)
    private String bankAccountHolder;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "seniority_start_date", nullable = false)
    private LocalDate seniorityStartDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 20)
    private EmploymentStatus employmentStatus;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "termination_reason", columnDefinition = "TEXT")
    private String terminationReason;

    /**
     * Set while an employment end waits for its last working day ({@code terminationDate}) to pass:
     * the status the employee then takes, and who scheduled it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scheduled_end_status", length = 20)
    private EmploymentStatus scheduledEndStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_end_by_account_id")
    private Account scheduledEndByAccount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_account_id")
    private Account deletedByAccount;

    @Column(name = "deletion_reason", columnDefinition = "TEXT")
    private String deletionReason;

    /** The first day worked in a period starting {@code periodStart}: the hire date when it is later. */
    public LocalDate employedFrom(LocalDate periodStart) {
        return hireDate.isAfter(periodStart) ? hireDate : periodStart;
    }

    /** The last day worked in a period ending {@code periodEnd}: the last working day when it is earlier. */
    public LocalDate employedTo(LocalDate periodEnd) {
        return terminationDate != null && terminationDate.isBefore(periodEnd) ? terminationDate : periodEnd;
    }

    /** Whether the employee works at least one day in [{@code from}, {@code to}]. */
    public boolean isEmployedDuring(LocalDate from, LocalDate to) {
        return !hireDate.isAfter(to) && (terminationDate == null || !terminationDate.isBefore(from));
    }
}
