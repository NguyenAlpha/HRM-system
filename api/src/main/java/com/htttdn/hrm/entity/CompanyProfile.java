package com.htttdn.hrm.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "company_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyProfile extends AuditedEntity {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "tax_code", unique = true, length = 30)
    private String taxCode;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false, length = 50)
    private String timezone;

    /** Logo image bytes; null when the company has none. Set together with its content type and timestamp. */
    @Column(columnDefinition = "BYTEA")
    private byte[] logo;

    @Column(name = "logo_content_type", length = 20)
    private String logoContentType;

    @Column(name = "logo_updated_at")
    private Instant logoUpdatedAt;
}
