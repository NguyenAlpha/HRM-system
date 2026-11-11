package com.htttdn.hrm.entity;

import com.htttdn.hrm.entity.enums.PermissionAssignmentPolicy;
import com.htttdn.hrm.entity.enums.PermissionModule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PermissionModule module;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_policy", nullable = false, length = 20)
    private PermissionAssignmentPolicy assignmentPolicy;

    /** Chỉ có nghĩa ở phạm vi toàn công ty: role chứa permission này chỉ được gán với scope {@code COMPANY}. */
    @Column(name = "company_wide", nullable = false)
    private boolean companyWide;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** Đoạn mã của permission tự phục vụ, VD {@code payroll.self.read}: chỉ tác động lên dữ liệu của chính mình. */
    public static final String SELF_SERVICE_MARKER = ".self.";

    /** Permission tự phục vụ: role có ít nhất một permission như vậy mới gán được với scope {@code SELF}. */
    public boolean isSelfService() {
        return code.contains(SELF_SERVICE_MARKER);
    }
}
