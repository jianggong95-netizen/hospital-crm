package com.hospital.crm.system.security;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.org.SysOrgRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeServiceTest {

    private final SysOrgRepository orgRepository = mock(SysOrgRepository.class);
    private final DataScopeService dataScopeService = new DataScopeService(orgRepository);

    @Test
    void allScopeGrantsEverything() {
        DataScope scope = dataScopeService.resolve(principal(List.of(new RoleScope(DataScopeType.ALL, Set.of()))));

        assertTrue(scope.all());
        assertTrue(scope.orgIds().isEmpty());
        assertFalse(scope.includeSelf());
    }

    @Test
    void orgScopeLimitsToOwnOrg() {
        UUID orgId = UUID.randomUUID();
        DataScope scope = dataScopeService.resolve(principal(orgId,
                List.of(new RoleScope(DataScopeType.ORG, Set.of()))));

        assertFalse(scope.all());
        assertEquals(Set.of(orgId), scope.orgIds());
        assertTrue(scope.allowsOrg(orgId));
        assertFalse(scope.allowsOrg(UUID.randomUUID()));
    }

    @Test
    void orgAndChildrenExpandsByOrgPath() {
        UUID orgId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(orgRepository.findIdsByOrgPathPrefix("/root/" + orgId + "/")).thenReturn(List.of(orgId, childId));

        DataScope scope = dataScopeService.resolve(principal(orgId,
                List.of(new RoleScope(DataScopeType.ORG_AND_CHILDREN, Set.of()))));

        assertEquals(Set.of(orgId, childId), scope.orgIds());
        assertTrue(scope.allowsOrg(childId));
    }

    @Test
    void customScopeUsesConfiguredOrgs() {
        UUID orgId = UUID.randomUUID();
        UUID customId = UUID.randomUUID();
        DataScope scope = dataScopeService.resolve(principal(orgId,
                List.of(new RoleScope(DataScopeType.CUSTOM, Set.of(customId)))));

        assertEquals(Set.of(customId), scope.orgIds());
        assertFalse(scope.allowsOrg(orgId));
    }

    @Test
    void selfScopeOnlyIncludesCurrentUser() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "staff", "员工", UUID.randomUUID(), "门诊",
                "/root/dept/", Set.of("STAFF"), Set.of(),
                List.of(new RoleScope(DataScopeType.SELF, Set.of())));

        DataScope scope = dataScopeService.resolve(principal);

        assertFalse(scope.all());
        assertTrue(scope.orgIds().isEmpty());
        assertTrue(scope.includeSelf());
        assertEquals(userId, scope.userId());
    }

    @Test
    void multipleScopesAreMerged() {
        UUID orgId = UUID.randomUUID();
        UUID customId = UUID.randomUUID();
        DataScope scope = dataScopeService.resolve(principal(orgId, List.of(
                new RoleScope(DataScopeType.ORG, Set.of()),
                new RoleScope(DataScopeType.CUSTOM, Set.of(customId)),
                new RoleScope(DataScopeType.SELF, Set.of()))));

        assertEquals(Set.of(orgId, customId), scope.orgIds());
        assertTrue(scope.includeSelf());
    }

    @Test
    void rejectsUnknownScopeCode() {
        assertThrows(BusinessException.class, () -> DataScopeType.fromCode("unknown"));
    }

    private UserPrincipal principal(List<RoleScope> roleScopes) {
        return principal(UUID.randomUUID(), roleScopes);
    }

    private UserPrincipal principal(UUID orgId, List<RoleScope> roleScopes) {
        return new UserPrincipal(UUID.randomUUID(), "staff", "员工", orgId, "门诊部",
                "/root/" + orgId + "/", Set.of("STAFF"), Set.of(), roleScopes);
    }
}
