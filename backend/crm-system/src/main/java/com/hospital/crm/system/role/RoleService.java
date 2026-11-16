package com.hospital.crm.system.role;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.org.SysOrg;
import com.hospital.crm.system.org.SysOrgRepository;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.security.DataScopeType;
import com.hospital.crm.system.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysOrgRepository orgRepository;
    private final AuditService auditService;

    public RoleService(SysRoleRepository roleRepository,
                       SysPermissionRepository permissionRepository,
                       SysOrgRepository orgRepository,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.orgRepository = orgRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.RoleResponse> list() {
        return roleRepository.findAllWithPermissions().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.PermissionGroup> permissions() {
        Map<String, List<RoleDtos.PermissionItem>> grouped = new LinkedHashMap<>();
        for (SysPermission permission : permissionRepository
                .findAllByOrderByModuleAscSortOrderAscPermissionCodeAsc()) {
            grouped.computeIfAbsent(permission.getModule(), key -> new ArrayList<>())
                    .add(new RoleDtos.PermissionItem(
                            permission.getId(),
                            permission.getPermissionCode(),
                            permission.getPermissionName(),
                            permission.getModule(),
                            permission.getPermissionType(),
                            permission.getDescription(),
                            permission.getSortOrder()));
        }
        return grouped.entrySet().stream()
                .map(entry -> new RoleDtos.PermissionGroup(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional
    public RoleDtos.RoleResponse create(RoleDtos.CreateRequest request) {
        DataScopeType dataScope = DataScopeType.fromCode(request.dataScope());
        ensureCanManageScope(dataScope);
        String roleCode = request.roleCode().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByRoleCode(roleCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "角色编码已存在");
        }
        Set<SysPermission> permissions = resolvePermissions(request.permissionCodes());
        ensureCanGrantPermissions(permissions);
        Set<UUID> customOrgIds = resolveCustomOrgIds(dataScope, request.orgIds());

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        role.setDataScope(dataScope);
        role.setStatus("active");
        role.setPermissions(permissions);
        role.setCustomOrgIds(customOrgIds);
        role = roleRepository.save(role);

        auditService.record("ROLE_CREATE", "ROLE", role.getId().toString(),
                roleDetail(role));
        return toResponse(role);
    }

    @Transactional
    public RoleDtos.RoleResponse update(UUID roleId, RoleDtos.UpdateRequest request) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));
        if ("SUPER_ADMIN".equals(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "内置超级管理员角色不允许修改");
        }
        DataScopeType dataScope = DataScopeType.fromCode(request.dataScope());
        ensureCanManageScope(dataScope);
        Set<SysPermission> permissions = resolvePermissions(request.permissionCodes());
        ensureCanGrantPermissions(permissions);
        Set<UUID> customOrgIds = resolveCustomOrgIds(dataScope, request.orgIds());

        Map<String, Object> before = roleDetail(role);
        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        role.setDataScope(dataScope);
        role.setStatus(request.status() == null || request.status().isBlank()
                ? role.getStatus() : request.status());
        role.setPermissions(permissions);
        role.setCustomOrgIds(customOrgIds);
        role = roleRepository.save(role);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", roleDetail(role));
        auditService.record("ROLE_UPDATE", "ROLE", role.getId().toString(), detail);

        return toResponse(role);
    }

    private Set<SysPermission> resolvePermissions(List<String> codes) {
        Set<String> normalized = codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysPermission> permissions = permissionRepository.findByPermissionCodeIn(normalized);
        if (permissions.size() != normalized.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "包含不存在的权限项");
        }
        return new HashSet<>(permissions);
    }

    private Set<UUID> resolveCustomOrgIds(DataScopeType dataScope, List<UUID> orgIds) {
        if (dataScope != DataScopeType.CUSTOM) {
            return new HashSet<>();
        }
        if (orgIds == null || orgIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "自定义数据范围必须选择组织");
        }
        Set<UUID> unique = new HashSet<>(orgIds);
        List<SysOrg> orgs = orgRepository.findAllById(unique);
        if (orgs.size() != unique.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "自定义数据范围包含不存在的组织");
        }
        return unique;
    }

    private void ensureCanManageScope(DataScopeType dataScope) {
        UserPrincipal actor = CurrentUser.principal();
        boolean all = actor.roleScopes().stream()
                .anyMatch(scope -> scope.type() == DataScopeType.ALL);
        if (dataScope == DataScopeType.ALL && !all) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有全院数据范围的管理员才能配置全院角色");
        }
    }

    private void ensureCanGrantPermissions(Set<SysPermission> permissions) {
        UserPrincipal actor = CurrentUser.principal();
        if (actor.roleCodes().contains("SUPER_ADMIN")) {
            return;
        }
        Set<String> actorPermissions = actor.permissions();
        boolean exceeds = permissions.stream()
                .map(SysPermission::getPermissionCode)
                .anyMatch(code -> !actorPermissions.contains(code));
        if (exceeds) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能授予超出自身权限范围的权限");
        }
    }

    private Map<String, Object> roleDetail(SysRole role) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("roleCode", role.getRoleCode());
        detail.put("roleName", role.getRoleName());
        detail.put("dataScope", role.getDataScope().code());
        detail.put("status", role.getStatus());
        detail.put("permissions", role.getPermissions().stream()
                .map(SysPermission::getPermissionCode).sorted().toList());
        detail.put("orgIds", role.getCustomOrgIds().stream().map(UUID::toString).sorted().toList());
        return detail;
    }

    private RoleDtos.RoleResponse toResponse(SysRole role) {
        List<String> permissionCodes = role.getPermissions().stream()
                .map(SysPermission::getPermissionCode)
                .sorted()
                .toList();
        List<UUID> orgIds = role.getCustomOrgIds().stream().sorted().toList();
        return new RoleDtos.RoleResponse(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getDataScope().code(),
                role.getStatus(),
                permissionCodes,
                orgIds,
                role.getCreatedAt()
        );
    }
}
