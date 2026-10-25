package com.htttdn.hrm.config.seed;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.htttdn.hrm.config.seed.StaffSeeder.RoleRequestEntry;
import com.htttdn.hrm.config.seed.StaffSeeder.StaffEntry;
import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RoleAssignmentRequest;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.RoleAssignmentRequestStatus;
import com.htttdn.hrm.entity.enums.RoleGrantPolicy;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.RoleAssignmentRequestRepository;
import com.htttdn.hrm.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ghi yêu cầu cấp role cho account của một nhân viên được seed, như HR gửi và Company Owner quyết
 * định. Chỉ role cần Owner duyệt mới có yêu cầu chờ; yêu cầu đã duyệt tạo luôn phân quyền tương ứng.
 *
 * <p>{@code daysAgo} là ngày gửi; {@code effectiveFromDaysAgo} và {@code effectiveToDaysAgo} là hiệu
 * lực của role, số âm là ngày sắp tới. Scope {@code ORG_UNIT} và {@code LOCATION} lấy đơn vị và địa
 * điểm của phân công hiện hành, như role khai báo trong file seed.
 */
@Component
@RequiredArgsConstructor
class SeedRoleRequestWriter {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AccountRepository accountRepository;
    private final AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    private final RoleAssignmentRequestRepository roleAssignmentRequestRepository;
    private final RoleRepository roleRepository;
    private final SeedTimestamps seedTimestamps;

    void write(StaffEntry entry, Account account, OrganizationUnit unit, WorkLocation location, Account owner) {
        List<RoleRequestEntry> requests = entry.roleRequests();
        if (requests == null) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        Instant now = Instant.now();
        for (RoleRequestEntry item : requests) {
            Role role = roleRepository.findByCodeAndDeletedAtIsNull(item.role())
                .orElseThrow(() -> invalid(entry, "role '" + item.role() + "' not found"));
            Account requester = accountRepository.findByUsername(item.requestedBy())
                .orElseThrow(() -> invalid(entry, "requester '" + item.requestedBy() + "' not found"));
            RoleAssignmentRequestStatus status = item.status();
            if (role.getGrantPolicy() != RoleGrantPolicy.OWNER_APPROVAL || item.scope() == null || status == null
                || item.reason() == null || status != RoleAssignmentRequestStatus.PENDING && status != RoleAssignmentRequestStatus.APPROVED
                    && item.note() == null) {
                throw invalid(entry, "a role request needs an owner-approved role, scope, status and reason;"
                    + " rejected and cancelled requests need a note");
            }
            LocalDate effectiveFrom = today.minusDays(item.effectiveFromDaysAgo());
            LocalDate effectiveTo = item.effectiveToDaysAgo() == null ? null : today.minusDays(item.effectiveToDaysAgo());
            Instant requestedAt = today.minusDays(item.daysAgo()).atTime(LocalTime.of(10, 0)).atZone(ZONE).toInstant();
            Instant decidedAt = requestedAt.plus(Duration.ofDays(1));
            if (decidedAt.isAfter(now)) {
                decidedAt = now.minus(Duration.ofMinutes(30));
            }

            RoleAssignmentRequest request = RoleAssignmentRequest.builder()
                .account(account)
                .role(role)
                .scopeType(item.scope())
                .organizationUnit(item.scope() == RoleScopeType.ORG_UNIT ? unit : null)
                .workLocation(item.scope() == RoleScopeType.LOCATION ? location : null)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .reason(item.reason())
                .status(status)
                .requestedByAccount(requester)
                .requestedAt(requestedAt)
                .build();
            switch (status) {
                case APPROVED, REJECTED -> {
                    request.setReviewedByAccount(owner);
                    request.setReviewedAt(decidedAt);
                    request.setReviewNote(item.note());
                }
                case CANCELLED -> {
                    request.setCancelledByAccount(requester);
                    request.setCancelledAt(decidedAt);
                    request.setCancellationReason(item.note());
                }
                case PENDING -> { }
            }
            if (status == RoleAssignmentRequestStatus.APPROVED) {
                AccountRoleAssignment assignment = accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
                    .account(account)
                    .role(role)
                    .scopeType(item.scope())
                    .organizationUnit(request.getOrganizationUnit())
                    .workLocation(request.getWorkLocation())
                    .effectiveFrom(effectiveFrom)
                    .effectiveTo(effectiveTo)
                    .grantedByAccount(owner)
                    .reason(item.reason())
                    .build());
                seedTimestamps.at(assignment, decidedAt);
                request.setAccountRoleAssignment(assignment);
            }
            roleAssignmentRequestRepository.save(request);
            seedTimestamps.set(request, null, status == RoleAssignmentRequestStatus.PENDING ? requestedAt : decidedAt);
        }
    }

    private static IllegalStateException invalid(StaffEntry entry, String message) {
        return new IllegalStateException("Staff seed '" + entry.username() + "': " + message);
    }
}
