package com.htttdn.hrm.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.AccountPermissionOverride;
import com.htttdn.hrm.entity.AccountRoleAssignment;
import com.htttdn.hrm.entity.Permission;
import com.htttdn.hrm.entity.Role;
import com.htttdn.hrm.entity.RolePermissionId;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.entity.enums.PermissionModule;
import com.htttdn.hrm.entity.enums.PermissionOverrideEffect;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.repository.AccountPermissionOverrideRepository;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.AccountRoleAssignmentRepository;
import com.htttdn.hrm.repository.PermissionRepository;
import com.htttdn.hrm.repository.RolePermissionRepository;
import com.htttdn.hrm.repository.RoleRepository;
import com.htttdn.hrm.security.JwtService;
import com.htttdn.hrm.service.AccountAuthorizationService.AuthorizationSnapshot;
import com.htttdn.hrm.service.PermissionService;
import com.htttdn.hrm.service.RoleService;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "company.seed.enabled=false",
    "rbac.seed.enabled=false",
    "admin.seed.enabled=false",
    "user.seed.enabled=false",
    "rbac.management.allowed-roles=SYSTEM_ADMIN"
})
@AutoConfigureMockMvc
@Transactional
class RbacManagementTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountRoleAssignmentRepository accountRoleAssignmentRepository;
    @Autowired private AccountPermissionOverrideRepository accountPermissionOverrideRepository;
    @Autowired private RoleService roleService;
    @Autowired private PermissionService permissionService;

    @ParameterizedTest
    @MethodSource("endpoints")
    void allEndpointsRequireAuthentication(String method, String path, String body) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void otherRolesCannotManageRbacEvenWithRbacPermission(String method, String path, String body) throws Exception {
        String token = token(42L, List.of("EMPLOYEE", "HR_STAFF"), List.of("rbac.manage"));
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanCreateReadUpdateAndSoftDeleteRole() throws Exception {
        String code = roleCode();
        long id = createdId(admin(post("/api/roles"), Map.of(
            "code", code, "name", "Custom role", "description", "Initial description"
        )).andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.isSystem").value(false))
            .andExpect(jsonPath("$.data.isActive").value(true)));

        admin(get("/api/roles/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value(code));
        admin(get("/api/roles").param("size", "1").param("sort", "id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(id));
        admin(put("/api/roles/{id}", id), Map.of(
            "name", "Updated role", "description", "Updated description", "isActive", false
        )).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Updated role"))
            .andExpect(jsonPath("$.data.isActive").value(false));
        admin(delete("/api/roles/{id}", id)).andExpect(status().isOk());

        Role deletedRole = roleRepository.findById(id).orElseThrow();
        assertNotNull(deletedRole.getDeletedAt());
        assertEquals(deletedRole.getDeletedAt(), deletedRole.getUpdatedAt());
        assertFalse(deletedRole.getIsActive());
        assertFalse(roleRepository.findByDeletedAtIsNull(Pageable.unpaged()).stream()
            .anyMatch(role -> role.getId().equals(id)));
        admin(get("/api/roles/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("ROLE_NOT_FOUND"));
        admin(post("/api/roles"), Map.of("code", code, "name", "Duplicate deleted role"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.field").value("code"));
        admin(delete("/api/roles/{id}/permissions/1", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("ROLE_NOT_FOUND"));
    }

    @Test
    void systemRolesCannotBeDeletedOrDeactivated() throws Exception {
        Role role = savedRole(true);
        admin(delete("/api/roles/{id}", role.getId()))
            .andExpect(status().isConflict());
        admin(put("/api/roles/{id}", role.getId()), Map.of("name", role.getName(), "isActive", false))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.field").value("isActive"));
    }

    @Test
    void adminCanCreateReadUpdateAndDeleteUnusedPermission() throws Exception {
        String code = permissionCode();
        Map<String, Object> body = Map.of(
            "code", code,
            "name", "Custom report",
            "module", "REPORT",
            "description", "Custom report permission"
        );
        long id = createdId(admin(post("/api/permissions"), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.isActive").value(true)));

        admin(post("/api/permissions"), body)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.field").value("code"));
        admin(get("/api/permissions/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value(code));
        admin(get("/api/permissions").param("size", "1").param("sort", "id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(id));
        admin(get("/api/permissions").param("module", "REPORT").param("size", "1").param("sort", "id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(id))
            .andExpect(jsonPath("$.data.content[0].module").value("REPORT"));
        admin(put("/api/permissions/{id}", id), Map.of(
                "name", "Updated report",
                "description", "Updated report permission",
                "isActive", false
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Updated report"))
            .andExpect(jsonPath("$.data.description").value("Updated report permission"))
            .andExpect(jsonPath("$.data.isActive").value(false));
        admin(delete("/api/permissions/{id}", id)).andExpect(status().isOk());
        assertFalse(permissionRepository.existsById(id));
        admin(get("/api/permissions/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_NOT_FOUND"));
    }

    @Test
    void adminCanGrantAndRevokePermissionWithActorTakenFromJwt() throws Exception {
        Role role = savedRole(false);
        Permission permission = savedPermission(permissionCode());
        Account actor = savedAccount();
        String token = token(actor.getId(), List.of("SYSTEM_ADMIN"), List.of());
        String path = "/api/roles/" + role.getId() + "/permissions";
        String body = objectMapper.writeValueAsString(Map.of(
            "permissionId", permission.getId(), "grantedByAccountId", -1
        ));

        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        RolePermissionId assignmentId = new RolePermissionId(role.getId(), permission.getId());
        assertEquals(actor.getId(), rolePermissionRepository.findById(assignmentId)
            .orElseThrow().getCreatedByAccount().getId());
        admin(get(path)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(permission.getId()));
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
        admin(delete("/api/permissions/{id}", permission.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        admin(delete(path + "/" + permission.getId())).andExpect(status().isOk());
        assertFalse(rolePermissionRepository.existsById(assignmentId));
        admin(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        admin(delete(path + "/" + permission.getId())).andExpect(status().isNotFound());
        admin(delete("/api/permissions/{id}", permission.getId())).andExpect(status().isOk());
    }

    @Test
    void permissionReferencedByHistoricalOverrideCannotBeDeleted() throws Exception {
        Account actor = savedAccount();
        Role role = savedRole(false);
        Permission permission = savedPermission(permissionCode());
        AccountRoleAssignment assignment = accountRoleAssignmentRepository.save(AccountRoleAssignment.builder()
            .account(actor).role(role).scopeType(RoleScopeType.COMPANY)
            .effectiveFrom(LocalDate.now().minusDays(2)).grantedByAccount(actor).createdAt(Instant.now()).build());
        accountPermissionOverrideRepository.save(AccountPermissionOverride.builder()
            .accountRoleAssignment(assignment).permission(permission).effect(PermissionOverrideEffect.GRANT)
            .effectiveFrom(LocalDate.now().minusDays(2)).effectiveTo(LocalDate.now().minusDays(1))
            .reason("Historical permission").grantedByAccount(actor).createdAt(Instant.now()).build());

        admin(delete("/api/permissions/{id}", permission.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void bootstrapPermissionCannotBeDeletedOrDeactivated() throws Exception {
        Permission permission = permissionRepository.findByCode("rbac.manage")
            .orElseGet(() -> savedPermission("rbac.manage"));
        admin(delete("/api/permissions/{id}", permission.getId())).andExpect(status().isConflict());
        admin(put("/api/permissions/{id}", permission.getId()), Map.of(
                "name", "Manage RBAC",
                "description", "RBAC",
                "isActive", false
            ))
            .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidBodiesReturnValidationErrors(String method, String path, String body, String field) throws Exception {
        admin(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.field").value(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/permissions?module=UNKNOWN", "/api/roles/invalid", "/api/permissions/invalid"})
    void invalidParametersReturnBadRequest(String path) throws Exception {
        admin(get(path)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingResourcesAndMalformedBodiesReturnExpectedErrors() throws Exception {
        admin(get("/api/roles/-1")).andExpect(status().isNotFound());
        admin(put("/api/roles/-1"), Map.of("name", "Missing", "isActive", true))
            .andExpect(status().isNotFound());
        admin(delete("/api/roles/-1")).andExpect(status().isNotFound());
        admin(get("/api/roles/-1/permissions")).andExpect(status().isNotFound());
        admin(post("/api/roles/-1/permissions"), Map.of("permissionId", 1))
            .andExpect(status().isNotFound());
        admin(get("/api/permissions/-1")).andExpect(status().isNotFound());
        admin(put("/api/permissions/-1"), Map.of(
                "name", "Missing",
                "description", "Missing",
                "isActive", true
            ))
            .andExpect(status().isNotFound());
        admin(delete("/api/permissions/-1")).andExpect(status().isNotFound());
        admin(post("/api/roles").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void serviceCallsAreProtectedWithoutGoingThroughControllers() {
        assertThrows(AccessDeniedException.class, () -> roleService.list(Pageable.unpaged()));
        assertThrows(AccessDeniedException.class, () -> permissionService.list(Pageable.unpaged()));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
            "Bearer " + token(42L, List.of("SYSTEM_ADMIN"), List.of())));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return admin(request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private long createdId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String token(Long accountId, List<String> roles, List<String> permissions) {
        return jwtService.generateAccessToken(Account.builder().id(accountId).username("rbac-test").build(),
            new AuthorizationSnapshot(roles, permissions));
    }

    private Role savedRole(boolean system) {
        return roleRepository.save(Role.builder().code(roleCode()).name("RBAC test role")
            .isSystem(system).isActive(true).createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    private Permission savedPermission(String code) {
        return permissionRepository.save(Permission.builder().code(code).name("RBAC test permission")
            .module(PermissionModule.RBAC)
            .description("RBAC test permission").isActive(true).createdAt(Instant.now()).build());
    }

    private Account savedAccount() {
        String username = "rbac_" + UUID.randomUUID().toString().replace("-", "");
        return accountRepository.save(Account.builder().username(username).email(username + "@hrm.test")
            .passwordHash("unused-in-bearer-token-tests").status(AccountStatus.ACTIVE).failedLoginCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    private String roleCode() {
        return "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    private String permissionCode() {
        return "test.permission_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static Stream<Arguments> endpoints() {
        return Stream.of(
            Arguments.of("POST", "/api/roles", "{\"code\":\"TEST_ROLE\",\"name\":\"Test role\"}"),
            Arguments.of("GET", "/api/roles", ""),
            Arguments.of("GET", "/api/roles/1", ""),
            Arguments.of("PUT", "/api/roles/1", "{\"name\":\"Updated\",\"isActive\":true}"),
            Arguments.of("DELETE", "/api/roles/1", ""),
            Arguments.of("GET", "/api/roles/1/permissions", ""),
            Arguments.of("POST", "/api/roles/1/permissions", "{\"permissionId\":1}"),
            Arguments.of("DELETE", "/api/roles/1/permissions/1", ""),
            Arguments.of("POST", "/api/permissions", "{\"code\":\"test.read\",\"name\":\"Test\",\"module\":\"RBAC\",\"description\":\"Test\"}"),
            Arguments.of("GET", "/api/permissions", ""),
            Arguments.of("GET", "/api/permissions?module=RBAC", ""),
            Arguments.of("GET", "/api/permissions/1", ""),
            Arguments.of("PUT", "/api/permissions/1", "{\"name\":\"Updated\",\"description\":\"Updated\",\"isActive\":true}"),
            Arguments.of("DELETE", "/api/permissions/1", "")
        );
    }

    private static Stream<Arguments> invalidBodies() {
        return Stream.of(
            Arguments.of("POST", "/api/roles", "{\"code\":\"ROLE_SYSTEM_ADMIN\",\"name\":\"Test\"}", "code"),
            Arguments.of("POST", "/api/roles", "{\"code\":\"lowercase\",\"name\":\"Test\"}", "code"),
            Arguments.of("POST", "/api/roles", "{\"code\":\"" + "A".repeat(51) + "\",\"name\":\"Test\"}", "code"),
            Arguments.of("POST", "/api/roles", "{\"code\":\"TEST\",\"name\":\"" + "A".repeat(151) + "\"}", "name"),
            Arguments.of("PUT", "/api/roles/1", "{\"name\":\"Test\"}", "isActive"),
            Arguments.of("POST", "/api/roles/1/permissions", "{\"permissionId\":0}", "permissionId"),
            Arguments.of("POST", "/api/permissions", "{\"code\":\"" + "a".repeat(101)
                + "\",\"name\":\"Test\",\"module\":\"RBAC\",\"description\":\"Test\"}", "code"),
            Arguments.of("POST", "/api/permissions", "{\"code\":\"ROLE_SYSTEM_ADMIN\",\"name\":\"Test\",\"module\":\"RBAC\",\"description\":\"Test\"}", "code"),
            Arguments.of("POST", "/api/permissions", "{\"code\":\"test.read\",\"name\":\"Test\",\"description\":\"Test\"}", "module"),
            Arguments.of("PUT", "/api/permissions/1", "{\"name\":\"Test\",\"description\":\"Test\"}", "isActive")
        );
    }
}
