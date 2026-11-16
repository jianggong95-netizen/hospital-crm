package com.hospital.crm.system.security;

import com.hospital.crm.system.org.SysOrg;
import com.hospital.crm.system.org.SysOrgRepository;
import com.hospital.crm.system.role.SysPermission;
import com.hospital.crm.system.role.SysRole;
import com.hospital.crm.system.user.SysUser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPrincipalFactoryTest {

    private final SysOrgRepository orgRepository = mock(SysOrgRepository.class);
    private final UserPrincipalFactory factory = new UserPrincipalFactory(orgRepository);

    @Test
    void aggregatesPermissionsFromActiveRolesOnly() {
        UUID orgId = UUID.randomUUID();
        SysOrg org = new SysOrg();
        org.setId(orgId);
        org.setOrgName("门诊部");
        org.setOrgPath("/root/" + orgId + "/");
        when(orgRepository.findById(orgId)).thenReturn(Optional.of(org));

        SysPermission readPermission = permission("system:user:read");
        SysPermission writePermission = permission("system:user:write");

        SysRole activeRole = role("DOCTOR", "active", DataScopeType.ORG, Set.of(readPermission));
        SysRole disabledRole = role("TEMP_ADMIN", "disabled", DataScopeType.ALL, Set.of(writePermission));

        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername("doctor01");
        user.setDisplayName("张医生");
        user.setOrgId(orgId);
        user.setRoles(new HashSet<>(Set.of(activeRole, disabledRole)));

        UserPrincipal principal = factory.create(user);

        assertEquals(Set.of("DOCTOR"), principal.roleCodes());
        assertEquals(Set.of("system:user:read"), principal.permissions());
        assertEquals("门诊部", principal.orgName());
        assertFalse(principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("system:user:write")));
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DOCTOR")));
        assertEquals(List.of(new RoleScope(DataScopeType.ORG, Set.of())), principal.roleScopes());
    }

    private SysPermission permission(String code) {
        SysPermission permission = new SysPermission();
        permission.setPermissionCode(code);
        return permission;
    }

    private SysRole role(String code, String status, DataScopeType dataScope, Set<SysPermission> permissions) {
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(status);
        role.setDataScope(dataScope);
        role.setPermissions(new HashSet<>(permissions));
        return role;
    }
}
