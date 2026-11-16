package com.hospital.crm.system.user;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.org.SysOrg;
import com.hospital.crm.system.org.SysOrgRepository;
import com.hospital.crm.system.role.SysPermission;
import com.hospital.crm.system.role.SysRole;
import com.hospital.crm.system.role.SysRoleRepository;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.security.DataScope;
import com.hospital.crm.system.security.DataScopeService;
import com.hospital.crm.system.security.PasswordPolicy;
import com.hospital.crm.system.security.RefreshTokenService;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.support.MaskingUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final SysUserRepository userRepository;
    private final SysOrgRepository orgRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public UserService(SysUserRepository userRepository,
                       SysOrgRepository orgRepository,
                       SysRoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       DataScopeService dataScopeService,
                       RefreshTokenService refreshTokenService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataScopeService = dataScopeService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.UserResponse> list(int page, int pageSize, String keyword,
                                                    UUID orgId, String status) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        DataScope scope = dataScopeService.current();
        Specification<SysUser> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), pattern)
                ));
            }
            if (orgId != null) {
                predicates.add(criteriaBuilder.equal(root.get("orgId"), orgId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (!scope.all()) {
                List<Predicate> scopePredicates = new ArrayList<>();
                if (!scope.orgIds().isEmpty()) {
                    scopePredicates.add(root.get("orgId").in(scope.orgIds()));
                }
                if (scope.includeSelf()) {
                    scopePredicates.add(criteriaBuilder.equal(root.get("id"), scope.userId()));
                }
                predicates.add(scopePredicates.isEmpty()
                        ? criteriaBuilder.disjunction()
                        : criteriaBuilder.or(scopePredicates.toArray(Predicate[]::new)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        Page<SysUser> result = userRepository.findAll(specification, pageable);
        Map<UUID, String> orgNames = orgNames(result.getContent());
        List<UserDtos.UserResponse> items = result.getContent().stream()
                .map(user -> toResponse(user, orgNames))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public UserDtos.UserResponse create(UserDtos.CreateRequest request) {
        String username = MaskingUtils.normalizeUsername(request.username());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "用户名已存在");
        }
        SysOrg org = orgRepository.findById(request.orgId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "所属组织不存在"));
        List<SysRole> roles = resolveRoles(request.roleIds());
        ensureCanAssignRoles(roles);
        PasswordPolicy.validate(request.password());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(MaskingUtils.normalizeEmail(request.email()));
        user.setMobileMasked(MaskingUtils.maskMobile(request.mobile()));
        user.setOrgId(org.getId());
        user.setStatus("active");
        user.setRoles(new HashSet<>(roles));
        user = userRepository.save(user);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", username);
        detail.put("displayName", user.getDisplayName());
        detail.put("orgId", org.getId());
        detail.put("roles", roles.stream().map(SysRole::getRoleCode).sorted().toList());
        auditService.record("USER_CREATE", "USER", user.getId().toString(), detail);

        return toResponse(user, Map.of(org.getId(), org.getOrgName()));
    }

    @Transactional
    public UserDtos.UserResponse update(UUID userId, UserDtos.UpdateRequest request) {
        SysUser user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        ensureVisible(user);

        SysOrg org = orgRepository.findById(request.orgId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "所属组织不存在"));
        List<SysRole> roles = resolveRoles(request.roleIds());
        ensureCanAssignRoles(roles);

        String newStatus = request.status() == null || request.status().isBlank()
                ? user.getStatus() : request.status();
        if (userId.equals(CurrentUser.id()) && "disabled".equals(newStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "不能停用当前登录账号");
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("displayName", user.getDisplayName());
        before.put("orgId", user.getOrgId());
        before.put("roles", user.getRoles().stream().map(SysRole::getRoleCode).sorted().toList());
        before.put("status", user.getStatus());

        user.setDisplayName(request.displayName().trim());
        user.setEmail(MaskingUtils.normalizeEmail(request.email()));
        user.setMobileMasked(MaskingUtils.maskMobile(request.mobile()));
        user.setOrgId(org.getId());
        user.setStatus(newStatus);
        user.setRoles(new HashSet<>(roles));
        user = userRepository.save(user);

        if ("disabled".equals(newStatus)) {
            refreshTokenService.revokeAll(userId);
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("displayName", user.getDisplayName());
        after.put("orgId", user.getOrgId());
        after.put("roles", user.getRoles().stream().map(SysRole::getRoleCode).sorted().toList());
        after.put("status", user.getStatus());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        auditService.record("USER_UPDATE", "USER", userId.toString(), detail);

        return toResponse(user, Map.of(org.getId(), org.getOrgName()));
    }

    @Transactional
    public void changeStatus(UUID userId, UserDtos.StatusRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        ensureVisible(user);
        if (userId.equals(CurrentUser.id()) && "disabled".equals(request.status())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "不能停用当前登录账号");
        }
        String before = user.getStatus();
        user.setStatus(request.status());
        userRepository.save(user);
        if ("disabled".equals(request.status())) {
            refreshTokenService.revokeAll(userId);
        }
        auditService.record("USER_STATUS_CHANGED", "USER", userId.toString(),
                Map.of("before", before, "after", request.status()));
    }

    @Transactional
    public void resetPassword(UUID userId, UserDtos.PasswordResetRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        ensureVisible(user);
        PasswordPolicy.validate(request.password());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordUpdatedAt(Instant.now());
        userRepository.save(user);
        refreshTokenService.revokeAll(userId);
        auditService.record("USER_PASSWORD_RESET", "USER", userId.toString(),
                Map.of("username", user.getUsername()));
    }

    private List<SysRole> resolveRoles(List<UUID> roleIds) {
        List<SysRole> roles = roleRepository.findAllById(roleIds).stream()
                .filter(role -> "active".equals(role.getStatus()))
                .toList();
        if (roles.size() != new HashSet<>(roleIds).size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "包含不存在或已停用的角色");
        }
        return roles;
    }

    /**
     * 防止普通管理员通过分配角色获得超出自身的权限。
     */
    private void ensureCanAssignRoles(List<SysRole> roles) {
        UserPrincipal actor = CurrentUser.principal();
        if (actor.roleCodes().contains("SUPER_ADMIN")) {
            return;
        }
        Set<String> actorPermissions = actor.permissions();
        for (SysRole role : roles) {
            if ("SUPER_ADMIN".equals(role.getRoleCode())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只有超级管理员可以分配超级管理员角色");
            }
            boolean exceeds = role.getPermissions().stream()
                    .map(SysPermission::getPermissionCode)
                    .anyMatch(code -> !actorPermissions.contains(code));
            if (exceeds) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "不能分配超出自身权限范围的角色");
            }
        }
    }

    private void ensureVisible(SysUser user) {
        DataScope scope = dataScopeService.current();
        boolean visible = scope.all()
                || user.getId().equals(scope.userId())
                || (user.getOrgId() != null && scope.orgIds().contains(user.getOrgId()));
        if (!visible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该用户");
        }
    }

    private Map<UUID, String> orgNames(List<SysUser> users) {
        Set<UUID> orgIds = users.stream()
                .map(SysUser::getOrgId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        return orgRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(SysOrg::getId, SysOrg::getOrgName));
    }

    private UserDtos.UserResponse toResponse(SysUser user, Map<UUID, String> orgNames) {
        List<UserDtos.RoleSummary> roles = user.getRoles().stream()
                .sorted((left, right) -> left.getRoleCode().compareTo(right.getRoleCode()))
                .map(role -> new UserDtos.RoleSummary(role.getId(), role.getRoleCode(), role.getRoleName()))
                .toList();
        return new UserDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getMobileMasked(),
                user.getOrgId(),
                orgNames.getOrDefault(user.getOrgId(), null),
                user.getStatus(),
                roles,
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
