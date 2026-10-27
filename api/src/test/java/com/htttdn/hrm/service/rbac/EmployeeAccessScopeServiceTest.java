package com.htttdn.hrm.service.rbac;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.htttdn.hrm.config.ClockConfig;
import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.enums.RoleScopeType;
import com.htttdn.hrm.exception.ForbiddenException;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.EmployeeRepository;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;
import com.htttdn.hrm.service.rbac.AccountAuthorizationService.AuthorizationScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAccessScopeServiceTest {

    @Mock private AccountAuthorizationService accountAuthorizationService;
    @Mock private CurrentAccountProvider currentAccountProvider;
    @Mock private AccountRepository accountRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OrganizationUnitRepository organizationUnitRepository;
    @Mock private WorkLocationRepository workLocationRepository;

    @Test
    void expandsOrganizationUnitScopeToDescendants() {
        when(currentAccountProvider.accountId()).thenReturn(7L);
        when(accountAuthorizationService.getScopes(7L, "employee.read")).thenReturn(List.of(
            new AuthorizationScope(RoleScopeType.ORG_UNIT, 10L, null)
        ));
        when(organizationUnitRepository.findByParentUnitIdAndDeletedAtIsNull(10L)).thenReturn(List.of(
            OrganizationUnit.builder().id(11L).build()
        ));
        when(organizationUnitRepository.findByParentUnitIdAndDeletedAtIsNull(11L)).thenReturn(List.of());

        var scope = service().resolve("employee.read");

        assertFalse(scope.companyWide());
        assertEquals(Set.of(10L, 11L), scope.organizationUnitIds());
    }

    /** A SELF or COMPANY role has no unit or location: outside a company-wide scope that is a 403, not a 500. */
    @Test
    void destinationWithoutUnitOrLocationIsForbiddenBelowCompanyScope() {
        when(currentAccountProvider.accountId()).thenReturn(7L);
        when(accountAuthorizationService.getScopes(7L, "role.assignment.request")).thenReturn(List.of(
            new AuthorizationScope(RoleScopeType.ORG_UNIT, 10L, null)
        ));
        when(organizationUnitRepository.findByParentUnitIdAndDeletedAtIsNull(10L)).thenReturn(List.of());

        assertThrows(ForbiddenException.class,
            () -> service().requireDestinationAccess(null, null, "role.assignment.request"));
    }

    private EmployeeAccessScopeService service() {
        return new EmployeeAccessScopeService(
            accountAuthorizationService,
            currentAccountProvider,
            accountRepository,
            employeeRepository,
            organizationUnitRepository,
            workLocationRepository,
            Clock.system(ClockConfig.BUSINESS_ZONE)
        );
    }
}
