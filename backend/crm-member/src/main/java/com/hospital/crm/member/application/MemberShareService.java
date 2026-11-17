package com.hospital.crm.member.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.member.api.MemberDtos;
import com.hospital.crm.member.domain.MemberShare;
import com.hospital.crm.member.domain.MemberShareRepository;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 共享授权：资源所有人把余额/积分/次卡的消费权授予家庭成员，
 * 撤销后立即失效；已撤销授权不可重复撤销。
 */
@Service
public class MemberShareService {

    private final MemberShareRepository shareRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public MemberShareService(MemberShareRepository shareRepository,
                              CustomerService customerService,
                              AuditService auditService) {
        this.shareRepository = shareRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MemberDtos.ShareView> list(UUID ownerCustomerId, UUID beneficiaryCustomerId) {
        if (ownerCustomerId == null && beneficiaryCustomerId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请指定授权人或使用人");
        }
        List<MemberShare> shares = ownerCustomerId != null
                ? shareRepository.findByOwnerCustomerIdOrderByCreatedAtDesc(ownerCustomerId)
                : shareRepository.findByBeneficiaryCustomerIdAndStatusOrderByCreatedAtDesc(beneficiaryCustomerId, "ACTIVE");
        return shares.stream().map(this::toView).toList();
    }

    @Transactional
    public MemberDtos.ShareView create(MemberDtos.ShareCreateRequest request) {
        if (request.ownerCustomerId().equals(request.beneficiaryCustomerId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "不能将权益授权给自己");
        }
        customerService.brief(request.ownerCustomerId());
        customerService.brief(request.beneficiaryCustomerId());
        if (shareRepository.existsByOwnerCustomerIdAndBeneficiaryCustomerIdAndResourceTypeAndStatus(
                request.ownerCustomerId(), request.beneficiaryCustomerId(), request.resourceType(), "ACTIVE")) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该资源共享授权已存在");
        }
        if (request.validTo() != null && request.validTo().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "授权失效时间必须晚于当前时间");
        }

        MemberShare share = new MemberShare();
        share.setOwnerCustomerId(request.ownerCustomerId());
        share.setBeneficiaryCustomerId(request.beneficiaryCustomerId());
        share.setResourceType(request.resourceType());
        share.setResourceId(request.resourceId());
        share.setCanConsume(request.canConsume() == null ? Boolean.TRUE : request.canConsume());
        share.setValidFrom(Instant.now());
        share.setValidTo(request.validTo());
        share.setStatus("ACTIVE");
        MemberShare saved = shareRepository.save(share);
        auditService.record("MEMBER_SHARE_CREATE", "CRM_MEMBER_SHARE", saved.getId().toString(),
                Map.of("ownerCustomerId", request.ownerCustomerId().toString(),
                        "beneficiaryCustomerId", request.beneficiaryCustomerId().toString(),
                        "resourceType", request.resourceType()));
        return toView(saved);
    }

    @Transactional
    public MemberDtos.ShareView revoke(UUID shareId) {
        MemberShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "共享授权不存在"));
        if ("REVOKED".equals(share.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该共享授权已撤销，请勿重复操作");
        }
        share.setStatus("REVOKED");
        share.setRevokedAt(Instant.now());
        MemberShare saved = shareRepository.save(share);
        auditService.record("MEMBER_SHARE_REVOKE", "CRM_MEMBER_SHARE", saved.getId().toString(),
                Map.of("ownerCustomerId", saved.getOwnerCustomerId().toString()));
        return toView(saved);
    }

    private MemberDtos.ShareView toView(MemberShare share) {
        return new MemberDtos.ShareView(share.getId(), share.getOwnerCustomerId(),
                nameOf(share.getOwnerCustomerId()), share.getBeneficiaryCustomerId(),
                nameOf(share.getBeneficiaryCustomerId()), share.getResourceType(), share.getResourceId(),
                share.getCanConsume(), share.getValidFrom(), share.getValidTo(), share.getStatus(),
                share.getCreatedAt());
    }

    private String nameOf(UUID customerId) {
        try {
            return customerService.brief(customerId).name();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
