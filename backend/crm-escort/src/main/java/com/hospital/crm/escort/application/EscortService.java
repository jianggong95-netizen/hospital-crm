package com.hospital.crm.escort.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.escort.api.EscortDtos;
import com.hospital.crm.escort.domain.Escort;
import com.hospital.crm.escort.domain.EscortQualification;
import com.hospital.crm.escort.domain.EscortQualificationRepository;
import com.hospital.crm.escort.domain.EscortRepository;
import com.hospital.crm.escort.domain.EscortReviewRepository;
import com.hospital.crm.system.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 陪诊师档案与资质服务：手机号沿用预约中心相同的加密工具（AES-GCM 密文 + HMAC 哈希 + 脱敏三列），
 * 资质停用后不可参与派单，评分聚合来自评价表。
 */
@Service
public class EscortService {

    private static final String RESOURCE = "ESCORT";
    private static final String RESOURCE_QUALIFICATION = "ESCORT_QUALIFICATION";

    private final EscortRepository escortRepository;
    private final EscortQualificationRepository qualificationRepository;
    private final EscortReviewRepository reviewRepository;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public EscortService(EscortRepository escortRepository,
                         EscortQualificationRepository qualificationRepository,
                         EscortReviewRepository reviewRepository,
                         SensitiveDataCipher cipher,
                         SensitiveDataHasher hasher,
                         AuditService auditService) {
        this.escortRepository = escortRepository;
        this.qualificationRepository = qualificationRepository;
        this.reviewRepository = reviewRepository;
        this.cipher = cipher;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EscortDtos.EscortSummary> list(String keyword,
                                                       String status,
                                                       int page,
                                                       int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Escort> result = escortRepository.findAll(buildSpecification(keyword, status), pageable);
        Map<UUID, double[]> ratings = ratingSummary(result.getContent().stream()
                .map(Escort::getId).collect(Collectors.toList()));
        List<EscortDtos.EscortSummary> items = result.getContent().stream()
                .map(escort -> toSummary(escort, ratings.get(escort.getId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public EscortDtos.EscortDetail detail(UUID escortId) {
        Escort escort = load(escortId);
        Map<UUID, double[]> ratings = ratingSummary(List.of(escortId));
        List<EscortDtos.EscortQualificationView> qualifications =
                qualificationRepository.findByEscortIdOrderByIssuedAtDesc(escortId).stream()
                        .map(this::toQualificationView)
                        .toList();
        return new EscortDtos.EscortDetail(toSummary(escort, ratings.get(escortId)),
                escort.getRemark(), qualifications);
    }

    @Transactional
    public EscortDtos.EscortDetail create(EscortDtos.EscortCreateRequest request) {
        Escort escort = new Escort();
        escort.setEscortNo("ES" + escortRepository.nextEscortNo());
        escort.setName(request.name().trim());
        escort.setGender(request.gender() == null || request.gender().isBlank()
                ? "unknown" : request.gender());
        applyPhone(escort, request.phone());
        escort.setServiceAreas(joinAreas(request.serviceAreas()));
        escort.setStatus("active");
        escort.setRemark(trimToNull(request.remark()));
        escort = escortRepository.save(escort);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort.getEscortNo());
        auditService.record("ESCORT_CREATE", RESOURCE, escort.getId().toString(), audit);

        return detail(escort.getId());
    }

    @Transactional
    public EscortDtos.EscortDetail update(UUID escortId, EscortDtos.EscortUpdateRequest request) {
        Escort escort = load(escortId);
        if (request.name() != null && !request.name().isBlank()) {
            escort.setName(request.name().trim());
        }
        if (request.gender() != null && !request.gender().isBlank()) {
            escort.setGender(request.gender());
        }
        if (request.phone() != null) {
            applyPhone(escort, request.phone());
        }
        if (request.serviceAreas() != null) {
            escort.setServiceAreas(joinAreas(request.serviceAreas()));
        }
        if (request.remark() != null) {
            escort.setRemark(trimToNull(request.remark()));
        }
        escort = escortRepository.save(escort);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort.getEscortNo());
        auditService.record("ESCORT_UPDATE", RESOURCE, escortId.toString(), audit);

        return detail(escortId);
    }

    @Transactional
    public EscortDtos.EscortDetail changeStatus(UUID escortId, EscortDtos.EscortStatusRequest request) {
        Escort escort = load(escortId);
        escort.setStatus(request.status());
        escort = escortRepository.save(escort);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort.getEscortNo());
        audit.put("status", escort.getStatus());
        auditService.record("ESCORT_STATUS", RESOURCE, escortId.toString(), audit);

        return detail(escortId);
    }

    @Transactional
    public EscortDtos.EscortQualificationView addQualification(UUID escortId,
                                                                EscortDtos.QualificationCreateRequest request) {
        Escort escort = load(escortId);
        if (request.expiresAt() != null && request.expiresAt().isBefore(request.issuedAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "资质到期日期不能早于发证日期");
        }
        if (qualificationRepository.existsByEscortIdAndQualTypeAndCertNo(
                escortId, request.qualType(), request.certNo().trim())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该陪诊师已登记相同类型与编号的资质");
        }
        EscortQualification qualification = new EscortQualification();
        qualification.setEscortId(escortId);
        qualification.setQualType(request.qualType());
        qualification.setCertNo(request.certNo().trim());
        qualification.setIssuedAt(request.issuedAt());
        qualification.setExpiresAt(request.expiresAt());
        qualification.setStatus("active");
        qualification = qualificationRepository.save(qualification);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort.getEscortNo());
        audit.put("certNo", qualification.getCertNo());
        auditService.record("ESCORTQUAL_CREATE", RESOURCE_QUALIFICATION,
                qualification.getId().toString(), audit);

        return toQualificationView(qualification);
    }

    @Transactional
    public EscortDtos.EscortQualificationView revokeQualification(UUID escortId, UUID qualificationId) {
        Escort escort = load(escortId);
        EscortQualification qualification = qualificationRepository
                .findByIdAndEscortId(qualificationId, escortId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "资质记录不存在"));
        if ("revoked".equals(qualification.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该资质已停用，请勿重复操作");
        }
        qualification.setStatus("revoked");
        qualification = qualificationRepository.save(qualification);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("escortNo", escort.getEscortNo());
        audit.put("certNo", qualification.getCertNo());
        auditService.record("ESCORTQUAL_REVOKE", RESOURCE_QUALIFICATION,
                qualification.getId().toString(), audit);

        return toQualificationView(qualification);
    }

    @Transactional(readOnly = true)
    public Escort load(UUID escortId) {
        return escortRepository.findById(escortId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "陪诊师不存在"));
    }

    /**
     * 批量拉取陪诊师评分聚合，key 为陪诊师 id，value 为 [平均分, 评价数]。
     */
    @Transactional(readOnly = true)
    public Map<UUID, double[]> ratingSummary(Collection<UUID> escortIds) {
        Map<UUID, double[]> result = new LinkedHashMap<>();
        if (escortIds == null || escortIds.isEmpty()) {
            return result;
        }
        for (Object[] row : reviewRepository.ratingSummary(escortIds)) {
            result.put((UUID) row[0], new double[]{
                    ((Number) row[1]).doubleValue(),
                    ((Number) row[2]).longValue()});
        }
        return result;
    }

    private void applyPhone(Escort escort, String phone) {
        String normalized = CustomerMasking.normalizePhone(phone);
        if (normalized == null) {
            escort.setPhoneEncrypted(null);
            escort.setPhoneHash(null);
            escort.setPhoneMasked(null);
            return;
        }
        escort.setPhoneEncrypted(cipher.encrypt(normalized));
        escort.setPhoneHash(hasher.hash(normalized));
        escort.setPhoneMasked(CustomerMasking.maskPhone(normalized));
    }

    private String joinAreas(List<String> areas) {
        if (areas == null || areas.isEmpty()) {
            return null;
        }
        String joined = areas.stream()
                .filter(area -> area != null && !area.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        if (joined.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "服务区域总长度不能超过 500");
        }
        return joined.isEmpty() ? null : joined;
    }

    private List<String> splitAreas(String areas) {
        if (areas == null || areas.isBlank()) {
            return List.of();
        }
        return Arrays.stream(areas.split(",")).map(String::trim)
                .filter(area -> !area.isEmpty()).toList();
    }

    private EscortDtos.EscortSummary toSummary(Escort escort, double[] rating) {
        return new EscortDtos.EscortSummary(
                escort.getId(),
                escort.getEscortNo(),
                escort.getName(),
                escort.getGender(),
                escort.getPhoneMasked(),
                splitAreas(escort.getServiceAreas()),
                escort.getStatus(),
                rating == null ? null : Math.round(rating[0] * 100) / 100.0,
                rating == null ? 0L : (long) rating[1],
                escort.getCreatedAt(),
                escort.getUpdatedAt());
    }

    private EscortDtos.EscortQualificationView toQualificationView(EscortQualification qualification) {
        boolean expired = qualification.getExpiresAt() != null
                && qualification.getExpiresAt().isBefore(LocalDate.now());
        return new EscortDtos.EscortQualificationView(
                qualification.getId(),
                qualification.getQualType(),
                qualification.getCertNo(),
                qualification.getIssuedAt(),
                qualification.getExpiresAt(),
                qualification.getStatus(),
                expired);
    }

    private Specification<Escort> buildSpecification(String keyword, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("escortNo")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
