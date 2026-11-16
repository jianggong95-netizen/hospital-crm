package com.hospital.crm.system.security;

import com.hospital.crm.system.org.SysOrg;
import com.hospital.crm.system.org.SysOrgRepository;
import com.hospital.crm.system.role.SysPermission;
import com.hospital.crm.system.role.SysRole;
import com.hospital.crm.system.user.SysUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserPrincipalFactory {

    private final SysOrgRepository orgRepository;

    public UserPrincipalFactory(SysOrgRepository orgRepository) {
        this.orgRepository = orgRepository;
    }

    public UserPrincipal create(SysUser user) {
        SysOrg org = user.getOrgId() == null
                ? null
                : orgRepository.findById(user.getOrgId()).orElse(null);

        List<SysRole> activeRoles = user.getRoles().stream()
                .filter(role -> "active".equals(role.getStatus()))
                .toList();

        Set<String> roleCodes = activeRoles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> permissions = activeRoles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toUnmodifiableSet());
        List<RoleScope> roleScopes = activeRoles.stream()
                .map(role -> new RoleScope(role.getDataScope(), Set.copyOf(role.getCustomOrgIds())))
                .toList();

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getOrgId(),
                org == null ? null : org.getOrgName(),
                org == null ? null : org.getOrgPath(),
                roleCodes,
                permissions,
                roleScopes
        );
    }
}
