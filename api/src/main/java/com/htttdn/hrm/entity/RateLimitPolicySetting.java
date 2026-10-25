package com.htttdn.hrm.entity;

import java.time.Instant;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rate-limit policy as edited by an administrator, replacing its application.properties quota.
 * {@code policy} is the {@link com.htttdn.hrm.ratelimit.RateLimitPolicy#key() key}.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "rate_limit_policy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitPolicySetting {

    @Id
    @Column(length = 30)
    private String policy;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "max_requests", nullable = false)
    private int maxRequests;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by_account_id", nullable = false)
    private Account updatedByAccount;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
