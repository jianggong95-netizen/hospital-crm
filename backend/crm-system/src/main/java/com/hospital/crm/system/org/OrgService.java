package com.hospital.crm.system.org;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.security.DataScope;
import com.hospital.crm.system.security.DataScopeService;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.user.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrgService {

    private final SysOrgRepository orgRepository;
    private final SysUserRepository userRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public OrgService(SysOrgRepository orgRepository,
                      SysUserRepository userRepository,
                      DataScopeService dataScopeService,
                      AuditService auditService) {
        this.orgRepository = orgRepository;
        this.userRepository = userRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OrgDtos.OrgNode> tree() {
        List<SysOrg> all = orgRepository.findAllByOrderBySortOrderAscOrgNameAsc();
        UserPrincipal principal = CurrentUser.principal();
        DataScope scope = dataScopeService.resolve(principal);
        if (!scope.all()) {
            Set<UUID> visible = new HashSet<>(scope.orgIds());
            if (scope.includeSelf() && principal.orgId() != null) {
                visible.add(principal.orgId());
            }
            Map<UUID, SysOrg> byId = all.stream().collect(Collectors.toMap(SysOrg::getId, org -> org));
            Set<UUID> withAncestors = new HashSet<>();
            for (UUID orgId : visible) {
                SysOrg current = byId.get(orgId);
                while (current != null && withAncestors.add(current.getId())) {
                    current = current.getParentId() == null ? null : byId.get(current.getParentId());
                }
            }
            all = all.stream().filter(org -> withAncestors.contains(org.getId())).toList();
        }
        return buildTree(all);
    }

    @Transactional
    public OrgDtos.OrgNode create(OrgDtos.CreateRequest request) {
        DataScope scope = dataScopeService.current();
        if (!scope.allowsOrg(request.parentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权在该组织下创建子组织");
        }
        SysOrg parent = orgRepository.findById(request.parentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "上级组织不存在"));
        String orgCode = request.orgCode().trim().toUpperCase(Locale.ROOT);
        if (orgRepository.existsByOrgCode(orgCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "组织编码已存在");
        }

        SysOrg org = new SysOrg();
        org.setOrgCode(orgCode);
        org.setOrgName(request.orgName().trim());
        org.setParentId(parent.getId());
        org.setOrgPath("");
        org.setOrgType(request.orgType() == null || request.orgType().isBlank()
                ? "department" : request.orgType().trim());
        org.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        org.setStatus("active");
        org = orgRepository.save(org);

        org.setOrgPath(parent.getOrgPath() + org.getId() + "/");
        org = orgRepository.save(org);

        auditService.record("ORG_CREATE", "ORG", org.getId().toString(),
                Map.of("orgCode", org.getOrgCode(), "orgName", org.getOrgName(), "parentId", parent.getId().toString()));
        return toNode(org, List.of());
    }

    @Transactional
    public OrgDtos.OrgNode update(UUID orgId, OrgDtos.UpdateRequest request) {
        DataScope scope = dataScopeService.current();
        if (!scope.allowsOrg(orgId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该组织");
        }
        SysOrg org = orgRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "组织不存在"));

        String newStatus = request.status() == null ? org.getStatus() : request.status();
        if ("disabled".equals(newStatus) && !"disabled".equals(org.getStatus())) {
            if (org.getParentId() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "根组织不允许停用");
            }
            if (orgRepository.existsByParentId(orgId)) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该组织存在下级组织，不能停用");
            }
            if (userRepository.countByOrgId(orgId) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该组织存在关联用户，不能停用");
            }
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("orgName", org.getOrgName());
        before.put("orgType", org.getOrgType());
        before.put("sortOrder", org.getSortOrder());
        before.put("status", org.getStatus());

        org.setOrgName(request.orgName().trim());
        org.setOrgType(request.orgType() == null || request.orgType().isBlank()
                ? org.getOrgType() : request.orgType().trim());
        org.setSortOrder(request.sortOrder() == null ? org.getSortOrder() : request.sortOrder());
        org.setStatus(newStatus);
        org = orgRepository.save(org);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", Map.of("orgName", org.getOrgName(), "orgType", org.getOrgType(),
                "sortOrder", org.getSortOrder(), "status", org.getStatus()));
        auditService.record("ORG_UPDATE", "ORG", org.getId().toString(), detail);
        return toNode(org, List.of());
    }

    private List<OrgDtos.OrgNode> buildTree(List<SysOrg> orgs) {
        Map<UUID, List<SysOrg>> childrenByParent = new HashMap<>();
        List<SysOrg> roots = new ArrayList<>();
        for (SysOrg org : orgs) {
            if (org.getParentId() == null) {
                roots.add(org);
            } else {
                childrenByParent.computeIfAbsent(org.getParentId(), key -> new ArrayList<>()).add(org);
            }
        }
        Comparator<SysOrg> comparator = Comparator
                .comparingInt(SysOrg::getSortOrder)
                .thenComparing(SysOrg::getOrgName);
        roots.sort(comparator);
        return roots.stream().map(org -> toNode(org, childrenByParent, comparator)).toList();
    }

    private OrgDtos.OrgNode toNode(SysOrg org, List<OrgDtos.OrgNode> children) {
        return new OrgDtos.OrgNode(org.getId(), org.getOrgCode(), org.getOrgName(), org.getParentId(),
                org.getOrgType(), org.getSortOrder(), org.getStatus(), children);
    }

    private OrgDtos.OrgNode toNode(SysOrg org, Map<UUID, List<SysOrg>> childrenByParent,
                                   Comparator<SysOrg> comparator) {
        List<SysOrg> children = childrenByParent.getOrDefault(org.getId(), List.of());
        List<SysOrg> sorted = children.stream().sorted(comparator).toList();
        List<OrgDtos.OrgNode> childNodes = sorted.stream()
                .map(child -> toNode(child, childrenByParent, comparator))
                .toList();
        return toNode(org, childNodes);
    }
}
