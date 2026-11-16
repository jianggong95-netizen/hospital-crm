package com.hospital.crm.family.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.family.api.FamilyDtos;
import com.hospital.crm.family.domain.Family;
import com.hospital.crm.family.domain.FamilyAuthorization;
import com.hospital.crm.family.domain.FamilyAuthorizationRepository;
import com.hospital.crm.family.domain.FamilyMember;
import com.hospital.crm.family.domain.FamilyMemberRepository;
import com.hospital.crm.family.domain.FamilyRepository;
import com.hospital.crm.family.support.FamilyPolicy;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FamilyService {

    private static final String RESOURCE_TYPE = "FAMILY";
    private static final String ACTIVE = FamilyPolicy.STATUS_ACTIVE;
    private static final String ENDED = FamilyPolicy.STATUS_ENDED;
    private static final String REVOKED = "revoked";

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository memberRepository;
    private final FamilyAuthorizationRepository authorizationRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public FamilyService(FamilyRepository familyRepository,
                         FamilyMemberRepository memberRepository,
                         FamilyAuthorizationRepository authorizationRepository,
                         CustomerService customerService,
                         AuditService auditService) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
        this.authorizationRepository = authorizationRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<FamilyDtos.FamilySummary> list(String keyword, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Family> result = familyRepository.findAll(buildSpecification(keyword, status), pageable);
        List<UUID> familyIds = result.getContent().stream().map(Family::getId).toList();
        Map<UUID, List<FamilyMember>> membersByFamily = familyIds.isEmpty()
                ? Map.of()
                : memberRepository.findByFamilyIdInAndStatus(familyIds, ACTIVE).stream()
                .collect(java.util.stream.Collectors.groupingBy(FamilyMember::getFamilyId));

        List<UUID> adminCustomerIds = membersByFamily.values().stream()
                .flatMap(List::stream)
                .filter(FamilyMember::isAdmin)
                .map(FamilyMember::getCustomerId)
                .toList();
        Map<UUID, CustomerDtos.CustomerBrief> briefs = customerService.briefs(adminCustomerIds);

        List<FamilyDtos.FamilySummary> items = result.getContent().stream()
                .map(family -> toSummary(family, membersByFamily.getOrDefault(family.getId(), List.of()), briefs))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public FamilyDtos.FamilyDetail detail(UUID familyId) {
        Family family = load(familyId);
        List<FamilyMember> members =
                memberRepository.findByFamilyIdAndStatusOrderByAdminDescValidFromAsc(familyId, ACTIVE);
        return toDetail(family, members);
    }

    @Transactional(readOnly = true)
    public List<FamilyDtos.MemberItem> members(UUID familyId, boolean includeEnded) {
        load(familyId);
        List<FamilyMember> members = includeEnded
                ? memberRepository.findByFamilyIdOrderByValidFromDesc(familyId)
                : memberRepository.findByFamilyIdAndStatusOrderByAdminDescValidFromAsc(familyId, ACTIVE);
        Map<UUID, CustomerDtos.CustomerBrief> briefs = customerService.briefs(
                members.stream().map(FamilyMember::getCustomerId).toList());
        return members.stream()
                .map(member -> toMember(member, briefs.get(member.getCustomerId())))
                .toList();
    }

    /**
     * 查询客户当前有效的家庭归属，供患者360等聚合视图使用。
     * 家庭关系仅表达家庭归属，不代表病历或临床数据授权。
     */
    @Transactional(readOnly = true)
    public Optional<FamilyDtos.FamilyMembership> membershipOf(UUID customerId) {
        return memberRepository.findFirstByCustomerIdAndStatus(customerId, ACTIVE)
                .flatMap(member -> familyRepository.findById(member.getFamilyId())
                        .map(family -> new FamilyDtos.FamilyMembership(
                                family.getId(),
                                family.getFamilyNo(),
                                family.getFamilyName(),
                                family.getStatus(),
                                member.getRelationshipCode(),
                                FamilyPolicy.relationshipLabel(member.getRelationshipCode()),
                                member.isAdmin(),
                                memberRepository.findByFamilyIdAndStatusOrderByAdminDescValidFromAsc(
                                        family.getId(), ACTIVE).size(),
                                member.getValidFrom())));
    }

    @Transactional
    public FamilyDtos.FamilyDetail create(FamilyDtos.CreateRequest request) {
        CustomerDtos.CustomerBrief owner = customerService.brief(request.ownerCustomerId());
        if (!ACTIVE.equals(owner.status())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "家庭管理员客户档案已停用，不能创建家庭账户");
        }
        memberRepository.findFirstByCustomerIdAndStatus(owner.id(), ACTIVE).ifPresent(existing -> {
            String familyNo = familyRepository.findById(existing.getFamilyId())
                    .map(Family::getFamilyNo)
                    .orElse("");
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                    "该客户已属于家庭账户 " + familyNo + "，同一时段默认只允许一个有效家庭");
        });

        Family family = new Family();
        family.setFamilyNo(nextFamilyNo());
        family.setFamilyName(request.familyName().trim());
        family.setStatus(ACTIVE);
        family.setCreatedBy(CurrentUser.id());
        family = familyRepository.save(family);

        FamilyMember ownerMember = new FamilyMember();
        ownerMember.setFamilyId(family.getId());
        ownerMember.setCustomerId(owner.id());
        ownerMember.setRelationshipCode("self");
        ownerMember.setAdmin(true);
        ownerMember.setStatus(ACTIVE);
        ownerMember.setValidFrom(Instant.now());
        ownerMember = memberRepository.save(ownerMember);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("familyNo", family.getFamilyNo());
        detail.put("familyName", family.getFamilyName());
        detail.put("ownerCustomerNo", owner.customerNo());
        auditService.record("FAMILY_CREATE", RESOURCE_TYPE, family.getId().toString(), detail);

        return toDetail(family, List.of(ownerMember));
    }

    @Transactional
    public FamilyDtos.MemberItem addMember(UUID familyId, FamilyDtos.AddMemberRequest request) {
        Family family = loadActiveFamily(familyId);
        String relationship = FamilyPolicy.requireRelationshipCode(request.relationshipCode());
        CustomerDtos.CustomerBrief customer = customerService.brief(request.customerId());
        if (!ACTIVE.equals(customer.status())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "客户档案已停用，不能加入家庭");
        }
        if (memberRepository.existsByFamilyIdAndCustomerIdAndStatus(familyId, customer.id(), ACTIVE)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该客户已是本家庭成员");
        }
        memberRepository.findFirstByCustomerIdAndStatus(customer.id(), ACTIVE).ifPresent(existing -> {
            if (!existing.getFamilyId().equals(familyId)) {
                String otherFamilyNo = familyRepository.findById(existing.getFamilyId())
                        .map(Family::getFamilyNo)
                        .orElse("");
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                        "该客户已属于家庭账户 " + otherFamilyNo + "，同一时段默认只允许一个有效家庭");
            }
        });

        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setCustomerId(customer.id());
        member.setRelationshipCode(relationship);
        member.setAdmin(false);
        member.setStatus(ACTIVE);
        member.setValidFrom(Instant.now());
        member = memberRepository.save(member);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("familyNo", family.getFamilyNo());
        detail.put("memberId", member.getId().toString());
        detail.put("customerNo", customer.customerNo());
        detail.put("relationshipCode", relationship);
        auditService.record("FAMILY_MEMBER_ADDED", RESOURCE_TYPE, familyId.toString(), detail);

        return toMember(member, customer);
    }

    @Transactional
    public FamilyDtos.MemberItem updateMember(UUID familyId,
                                              UUID memberId,
                                              FamilyDtos.UpdateMemberRequest request) {
        Family family = loadActiveFamily(familyId);
        FamilyMember member = loadMember(familyId, memberId);
        if (ENDED.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该成员关系已解除，不允许修改");
        }

        List<String> changed = new ArrayList<>();
        String relationship = FamilyPolicy.requireRelationshipCode(request.relationshipCode());
        if (!relationship.equals(member.getRelationshipCode())) {
            member.setRelationshipCode(relationship);
            changed.add("relationshipCode");
        }

        if (Boolean.TRUE.equals(request.admin()) && !member.isAdmin()) {
            List<FamilyMember> currentAdmins =
                    memberRepository.findByFamilyIdAndAdminTrueAndStatus(familyId, ACTIVE);
            currentAdmins.forEach(admin -> admin.setAdmin(false));
            memberRepository.saveAll(currentAdmins);
            member.setAdmin(true);
            changed.add("admin");

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("familyNo", family.getFamilyNo());
            detail.put("adminMemberId", member.getId().toString());
            detail.put("adminCustomerId", member.getCustomerId().toString());
            auditService.record("FAMILY_ADMIN_CHANGED", RESOURCE_TYPE, familyId.toString(), detail);
        } else if (Boolean.FALSE.equals(request.admin()) && member.isAdmin()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "家庭必须保留一名管理员，请先将其他成员设为管理员");
        }

        member = memberRepository.save(member);

        if (!changed.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("familyNo", family.getFamilyNo());
            detail.put("memberId", member.getId().toString());
            detail.put("changedFields", changed);
            auditService.record("FAMILY_MEMBER_UPDATED", RESOURCE_TYPE, familyId.toString(), detail);
        }

        return toMember(member, customerService.brief(member.getCustomerId()));
    }

    /**
     * 解除成员关系：保留关系记录作为历史，并撤销该成员全部生效中的代办授权。
     */
    @Transactional
    public void endMembership(UUID familyId, UUID memberId) {
        Family family = loadActiveFamily(familyId);
        FamilyMember member = loadMember(familyId, memberId);
        if (ENDED.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该成员关系已解除");
        }
        if (member.isAdmin()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "家庭管理员不能直接解除关系，请先指定新的家庭管理员");
        }

        Instant now = Instant.now();
        member.setStatus(ENDED);
        member.setValidTo(now);
        memberRepository.save(member);

        List<FamilyAuthorization> activeAuthorizations =
                authorizationRepository.findByMemberIdAndStatus(memberId, ACTIVE);
        UUID operatorId = CurrentUser.id();
        activeAuthorizations.forEach(authorization -> {
            authorization.setStatus(REVOKED);
            authorization.setRevokedAt(now);
            authorization.setRevokedBy(operatorId);
        });
        authorizationRepository.saveAll(activeAuthorizations);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("familyNo", family.getFamilyNo());
        detail.put("memberId", memberId.toString());
        detail.put("customerId", member.getCustomerId().toString());
        detail.put("revokedAuthorizations", activeAuthorizations.size());
        auditService.record("FAMILY_RELATION_ENDED", RESOURCE_TYPE, familyId.toString(), detail);
    }

    @Transactional(readOnly = true)
    public List<FamilyDtos.AuthorizationItem> authorizations(UUID familyId, UUID memberId) {
        load(familyId);
        loadMember(familyId, memberId);
        return mapAuthorizations(authorizationRepository.findByMemberIdOrderByCreatedAtDesc(memberId));
    }

    @Transactional
    public FamilyDtos.AuthorizationItem createAuthorization(UUID familyId,
                                                            UUID memberId,
                                                            FamilyDtos.CreateAuthorizationRequest request) {
        Family family = loadActiveFamily(familyId);
        FamilyMember member = loadMember(familyId, memberId);
        if (ENDED.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该成员关系已解除，不能创建代办授权");
        }
        String scopeCode = FamilyPolicy.requireDelegationScope(request.scopeCode());
        if (member.getCustomerId().equals(request.granteeCustomerId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "被授权人不能是家庭成员本人");
        }
        if (!memberRepository.existsByFamilyIdAndCustomerIdAndStatus(
                familyId, request.granteeCustomerId(), ACTIVE)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "被授权人必须是同一家庭的有效成员");
        }
        if (authorizationRepository.existsByMemberIdAndGranteeCustomerIdAndScopeCodeAndStatus(
                memberId, request.granteeCustomerId(), scopeCode, ACTIVE)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                    "该代办授权已存在，如需调整请先撤销原授权");
        }

        Instant validFrom = request.validFrom() == null ? Instant.now() : request.validFrom();
        if (request.validTo() != null && !request.validTo().isAfter(validFrom)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "授权结束时间必须晚于开始时间");
        }

        FamilyAuthorization authorization = new FamilyAuthorization();
        authorization.setFamilyId(familyId);
        authorization.setMemberId(memberId);
        authorization.setGrantorCustomerId(member.getCustomerId());
        authorization.setGranteeCustomerId(request.granteeCustomerId());
        authorization.setScopeCode(scopeCode);
        authorization.setStatus(ACTIVE);
        authorization.setValidFrom(validFrom);
        authorization.setValidTo(request.validTo());
        authorization.setCreatedBy(CurrentUser.id());
        authorization = authorizationRepository.save(authorization);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("familyNo", family.getFamilyNo());
        detail.put("memberId", memberId.toString());
        detail.put("granteeCustomerId", request.granteeCustomerId().toString());
        detail.put("scopeCode", scopeCode);
        detail.put("authorizationId", authorization.getId().toString());
        auditService.record("FAMILY_AUTHORIZATION_CREATED", RESOURCE_TYPE, familyId.toString(), detail);

        return toAuthorization(authorization, customerService.brief(authorization.getGranteeCustomerId()));
    }

    @Transactional
    public FamilyDtos.AuthorizationItem revokeAuthorization(UUID familyId,
                                                            UUID memberId,
                                                            UUID authorizationId) {
        Family family = loadActiveFamily(familyId);
        FamilyAuthorization authorization = authorizationRepository
                .findByIdAndFamilyIdAndMemberId(authorizationId, familyId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "代办授权不存在"));
        if (REVOKED.equals(authorization.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该代办授权已撤销");
        }

        authorization.setStatus(REVOKED);
        authorization.setRevokedAt(Instant.now());
        authorization.setRevokedBy(CurrentUser.id());
        authorization = authorizationRepository.save(authorization);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("familyNo", family.getFamilyNo());
        detail.put("memberId", memberId.toString());
        detail.put("granteeCustomerId", authorization.getGranteeCustomerId().toString());
        detail.put("scopeCode", authorization.getScopeCode());
        detail.put("authorizationId", authorizationId.toString());
        auditService.record("FAMILY_AUTHORIZATION_REVOKED", RESOURCE_TYPE, familyId.toString(), detail);

        return toAuthorization(authorization,
                customerService.brief(authorization.getGranteeCustomerId()));
    }

    /**
     * 代办权限校验：供预约、支付等代办接口在受理前调用。
     * 授权撤销、过期、成员关系解除或家庭停用时，直接拒绝访问。
     */
    @Transactional(readOnly = true)
    public FamilyDtos.DelegationCheckResponse checkDelegation(UUID familyId,
                                                              FamilyDtos.DelegationCheckRequest request) {
        Family family = load(familyId);
        if (!ACTIVE.equals(family.getStatus())) {
            denyDelegation(family, request.memberId(), request.granteeCustomerId(), request.scopeCode(),
                    "家庭账户已停用，拒绝代办访问");
        }
        FamilyMember member = loadMember(familyId, request.memberId());
        String scopeCode = FamilyPolicy.requireDelegationScope(request.scopeCode());
        if (!ACTIVE.equals(member.getStatus())) {
            denyDelegation(family, request.memberId(), request.granteeCustomerId(), scopeCode,
                    "家庭成员关系已解除，拒绝代办访问");
        }
        if (member.getCustomerId().equals(request.granteeCustomerId())) {
            denyDelegation(family, request.memberId(), request.granteeCustomerId(), scopeCode,
                    "被授权人不能是家庭成员本人");
        }

        Optional<FamilyAuthorization> latest = authorizationRepository
                .findFirstByMemberIdAndGranteeCustomerIdAndScopeCodeOrderByCreatedAtDesc(
                        request.memberId(), request.granteeCustomerId(), scopeCode);
        if (latest.isEmpty()) {
            denyDelegation(family, request.memberId(), request.granteeCustomerId(), scopeCode,
                    "未找到有效代办授权，拒绝访问");
        }
        FamilyAuthorization authorization = latest.get();
        if (REVOKED.equals(authorization.getStatus())) {
            denyDelegation(family, request.memberId(), request.granteeCustomerId(), scopeCode,
                    "代办授权已撤销，拒绝访问");
        }
        if (!FamilyPolicy.isEffective(authorization.getStatus(), authorization.getValidFrom(),
                authorization.getValidTo(), Instant.now())) {
            denyDelegation(family, request.memberId(), request.granteeCustomerId(), scopeCode,
                    "代办授权已过期，拒绝访问");
        }

        return new FamilyDtos.DelegationCheckResponse(true,
                request.memberId(),
                request.granteeCustomerId(),
                scopeCode,
                authorization.getId(),
                authorization.getValidTo());
    }

    private void denyDelegation(Family family,
                                UUID memberId,
                                UUID granteeCustomerId,
                                String scopeCode,
                                String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("familyNo", family.getFamilyNo());
        detail.put("memberId", memberId.toString());
        detail.put("granteeCustomerId", granteeCustomerId.toString());
        detail.put("scopeCode", scopeCode);
        detail.put("reason", reason);
        auditService.recordNow("FAMILY_DELEGATION_DENIED", RESOURCE_TYPE, family.getId().toString(), detail);
        throw new BusinessException(ErrorCode.FORBIDDEN, reason);
    }

    private Specification<Family> buildSpecification(String keyword, String status) {
        String trimmedKeyword = blankToNull(keyword);
        String trimmedStatus = blankToNull(status);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (trimmedStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), trimmedStatus));
            }
            if (trimmedKeyword != null) {
                String like = "%" + escapeLike(trimmedKeyword.toLowerCase()) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("familyNo")), like, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("familyName")), like, '\\')));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Family load(UUID familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "家庭账户不存在"));
    }

    private Family loadActiveFamily(UUID familyId) {
        Family family = load(familyId);
        if (!ACTIVE.equals(family.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "家庭账户已停用，不允许变更");
        }
        return family;
    }

    private FamilyMember loadMember(UUID familyId, UUID memberId) {
        return memberRepository.findByIdAndFamilyId(memberId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "家庭成员不存在"));
    }

    private String nextFamilyNo() {
        Long sequence = familyRepository.nextFamilyNo();
        return "F" + (sequence == null ? "" : sequence);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private FamilyDtos.FamilySummary toSummary(Family family,
                                               List<FamilyMember> members,
                                               Map<UUID, CustomerDtos.CustomerBrief> briefs) {
        String adminName = members.stream()
                .filter(FamilyMember::isAdmin)
                .map(member -> briefs.get(member.getCustomerId()))
                .filter(java.util.Objects::nonNull)
                .map(CustomerDtos.CustomerBrief::name)
                .findFirst()
                .orElse(null);
        return new FamilyDtos.FamilySummary(
                family.getId(),
                family.getFamilyNo(),
                family.getFamilyName(),
                family.getStatus(),
                members.size(),
                adminName,
                family.getCreatedAt(),
                family.getUpdatedAt()
        );
    }

    private FamilyDtos.FamilyDetail toDetail(Family family, List<FamilyMember> members) {
        Map<UUID, CustomerDtos.CustomerBrief> briefs = customerService.briefs(
                members.stream().map(FamilyMember::getCustomerId).toList());
        List<FamilyDtos.MemberItem> items = members.stream()
                .map(member -> toMember(member, briefs.get(member.getCustomerId())))
                .toList();
        return new FamilyDtos.FamilyDetail(
                family.getId(),
                family.getFamilyNo(),
                family.getFamilyName(),
                family.getStatus(),
                items,
                family.getCreatedAt(),
                family.getUpdatedAt()
        );
    }

    private FamilyDtos.MemberItem toMember(FamilyMember member, CustomerDtos.CustomerBrief customer) {
        return new FamilyDtos.MemberItem(
                member.getId(),
                member.getFamilyId(),
                customer,
                member.getRelationshipCode(),
                FamilyPolicy.relationshipLabel(member.getRelationshipCode()),
                member.isAdmin(),
                member.getStatus(),
                member.getValidFrom(),
                member.getValidTo()
        );
    }

    private List<FamilyDtos.AuthorizationItem> mapAuthorizations(List<FamilyAuthorization> authorizations) {
        Map<UUID, CustomerDtos.CustomerBrief> briefs = customerService.briefs(
                authorizations.stream().map(FamilyAuthorization::getGranteeCustomerId).toList());
        return authorizations.stream()
                .map(authorization -> toAuthorization(authorization,
                        briefs.get(authorization.getGranteeCustomerId())))
                .toList();
    }

    private FamilyDtos.AuthorizationItem toAuthorization(FamilyAuthorization authorization,
                                                         CustomerDtos.CustomerBrief grantee) {
        return new FamilyDtos.AuthorizationItem(
                authorization.getId(),
                authorization.getMemberId(),
                grantee,
                authorization.getScopeCode(),
                FamilyPolicy.delegationScopeLabel(authorization.getScopeCode()),
                authorization.getStatus(),
                authorization.getValidFrom(),
                authorization.getValidTo(),
                authorization.getRevokedAt(),
                authorization.getCreatedAt()
        );
    }
}
