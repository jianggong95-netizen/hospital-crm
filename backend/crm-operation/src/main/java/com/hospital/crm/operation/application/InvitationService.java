package com.hospital.crm.operation.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.operation.api.OperationDtos;
import com.hospital.crm.operation.domain.Invitation;
import com.hospital.crm.operation.domain.InvitationRepository;
import com.hospital.crm.operation.domain.Lead;
import com.hospital.crm.operation.domain.LeadRepository;
import com.hospital.crm.operation.domain.SourceChannel;
import com.hospital.crm.operation.domain.SourceChannelRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 邀约任务服务：关联线索或客户，状态单向推进，接受邀约时同步线索为 INVITED。
 */
@Service
public class InvitationService {

    private static final String RESOURCE = "INVITATION";
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "PENDING", Set.of("CONTACTED", "CANCELLED", "EXPIRED"),
            "CONTACTED", Set.of("ACCEPTED", "DECLINED", "CANCELLED", "EXPIRED")
    );

    private final InvitationRepository invitationRepository;
    private final LeadRepository leadRepository;
    private final CustomerService customerService;
    private final SourceChannelRepository channelRepository;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public InvitationService(InvitationRepository invitationRepository,
                             LeadRepository leadRepository,
                             CustomerService customerService,
                             SourceChannelRepository channelRepository,
                             SysUserRepository userRepository,
                             AuditService auditService) {
        this.invitationRepository = invitationRepository;
        this.leadRepository = leadRepository;
        this.customerService = customerService;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationDtos.InvitationView> list(String keyword,
                                                           String status,
                                                           UUID ownerUserId,
                                                           int page,
                                                           int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Invitation> result = invitationRepository.findAll(buildSpecification(keyword, status, ownerUserId),
                pageable);
        MappingContext context = mappingContext(result.getContent());
        List<OperationDtos.InvitationView> items = result.getContent().stream()
                .map(invitation -> toView(invitation, context))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public OperationDtos.InvitationView detail(UUID invitationId) {
        Invitation invitation = load(invitationId);
        return toView(invitation, mappingContext(List.of(invitation)));
    }

    @Transactional
    public OperationDtos.InvitationView create(OperationDtos.InvitationCreateRequest request) {
        if (request.leadId() == null && request.customerId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邀约必须关联线索或客户");
        }
        if (request.leadId() != null) {
            Lead lead = leadRepository.findById(request.leadId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "关联线索不存在"));
            if ("MERGED".equals(lead.getStatus()) || "INVALID".equals(lead.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已合并或已作废线索不能创建邀约");
            }
        }
        if (request.customerId() != null) {
            customerService.brief(request.customerId());
        }
        String channelCode = requireChannel(request.channelCode());

        Invitation invitation = new Invitation();
        invitation.setInvitationNo("I" + invitationRepository.nextInvitationNo());
        invitation.setLeadId(request.leadId());
        invitation.setCustomerId(request.customerId());
        invitation.setChannelCode(channelCode);
        invitation.setTitle(request.title().trim());
        invitation.setPlannedAt(request.plannedAt());
        invitation.setOwnerUserId(resolveOwner(request.ownerUserId()));
        invitation.setOrgId(CurrentUser.principal().orgId());
        invitation.setStatus("PENDING");
        invitation.setRemark(blankToNull(request.remark()));
        invitation.setCreatedBy(CurrentUser.id());
        invitation = invitationRepository.save(invitation);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("invitationNo", invitation.getInvitationNo());
        audit.put("leadId", String.valueOf(invitation.getLeadId()));
        audit.put("customerId", String.valueOf(invitation.getCustomerId()));
        audit.put("plannedAt", invitation.getPlannedAt().toString());
        auditService.record("INVITATION_CREATE", RESOURCE, invitation.getId().toString(), audit);

        return toView(invitation, mappingContext(List.of(invitation)));
    }

    @Transactional
    public OperationDtos.InvitationView changeStatus(UUID invitationId,
                                                     OperationDtos.InvitationStatusRequest request) {
        Invitation invitation = load(invitationId);
        String target = request.status().trim().toUpperCase();
        Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(invitation.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "邀约状态不允许从 " + invitation.getStatus() + " 变更为 " + target);
        }
        String previous = invitation.getStatus();
        invitation.setStatus(target);
        if (request.result() != null) {
            invitation.setResult(blankToNull(request.result()));
        }
        if (request.remark() != null) {
            invitation.setRemark(blankToNull(request.remark()));
        }
        invitation = invitationRepository.save(invitation);

        if ("ACCEPTED".equals(target) && invitation.getLeadId() != null) {
            leadRepository.findById(invitation.getLeadId()).ifPresent(lead -> {
                if (Set.of("NEW", "CONTACTING", "QUALIFIED").contains(lead.getStatus())) {
                    lead.setStatus("INVITED");
                    lead.setUpdatedBy(CurrentUser.id());
                    leadRepository.save(lead);
                }
            });
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("invitationNo", invitation.getInvitationNo());
        audit.put("fromStatus", previous);
        audit.put("toStatus", target);
        auditService.record("INVITATION_STATUS_CHANGE", RESOURCE, invitationId.toString(), audit);

        return toView(invitation, mappingContext(List.of(invitation)));
    }

    private Specification<Invitation> buildSpecification(String keyword, String status, UUID owner) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (owner != null) {
                predicates.add(cb.equal(root.get("ownerUserId"), owner));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invitationNo")), like),
                        cb.like(cb.lower(root.get("title")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Invitation load(UUID invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "邀约任务不存在"));
    }

    private String requireChannel(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty() || !channelRepository.existsByChannelCode(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邀约渠道不存在：" + code);
        }
        return code;
    }

    private UUID resolveOwner(UUID requestedOwner) {
        UUID owner = requestedOwner == null ? CurrentUser.id() : requestedOwner;
        if (userRepository.findById(owner)
                .filter(user -> "active".equals(user.getStatus())).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邀约负责人不存在或已停用");
        }
        return owner;
    }

    private MappingContext mappingContext(Collection<Invitation> invitations) {
        Map<String, String> channelNames = channelRepository.findAllByOrderByChannelCodeAsc().stream()
                .collect(Collectors.toMap(SourceChannel::getChannelCode, SourceChannel::getChannelName,
                        (left, right) -> left));
        Set<UUID> leadIds = invitations.stream().map(Invitation::getLeadId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Lead> leads = leadIds.isEmpty() ? Map.of()
                : leadRepository.findByIdIn(leadIds).stream()
                .collect(Collectors.toMap(Lead::getId, lead -> lead));
        Set<UUID> customerIds = invitations.stream().map(Invitation::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerIds.isEmpty() ? Map.of()
                : customerService.briefs(customerIds);
        Set<UUID> ownerIds = invitations.stream().map(Invitation::getOwnerUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> ownerNames = ownerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (left, right) -> left));
        return new MappingContext(channelNames, leads, customers, ownerNames);
    }

    private OperationDtos.InvitationView toView(Invitation invitation, MappingContext context) {
        Lead lead = invitation.getLeadId() == null ? null : context.leads().get(invitation.getLeadId());
        CustomerDtos.CustomerBrief customer = invitation.getCustomerId() == null
                ? null : context.customers().get(invitation.getCustomerId());
        return new OperationDtos.InvitationView(
                invitation.getId(),
                invitation.getInvitationNo(),
                invitation.getLeadId(),
                lead == null ? null : lead.getLeadNo(),
                invitation.getCustomerId(),
                customer == null ? null : customer.customerNo(),
                customer == null ? null : customer.name(),
                invitation.getChannelCode(),
                context.channelNames().getOrDefault(invitation.getChannelCode(), invitation.getChannelCode()),
                invitation.getTitle(),
                invitation.getPlannedAt(),
                invitation.getOwnerUserId(),
                invitation.getOwnerUserId() == null ? null : context.ownerNames().get(invitation.getOwnerUserId()),
                invitation.getStatus(),
                invitation.getResult(),
                invitation.getRemark(),
                invitation.getCreatedAt(),
                invitation.getUpdatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record MappingContext(Map<String, String> channelNames,
                                  Map<UUID, Lead> leads,
                                  Map<UUID, CustomerDtos.CustomerBrief> customers,
                                  Map<UUID, String> ownerNames) {
    }
}
