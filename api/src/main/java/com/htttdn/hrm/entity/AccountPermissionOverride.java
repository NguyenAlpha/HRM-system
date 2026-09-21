package com.htttdn.hrm.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;

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
@Table(name = "account_permission_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_role_assignment_id", nullable = false)
    private AccountRoleAssignment accountRoleAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PermissionOverrideEffect effect;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_account_id", nullable = false)
    private Account grantedByAccount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
