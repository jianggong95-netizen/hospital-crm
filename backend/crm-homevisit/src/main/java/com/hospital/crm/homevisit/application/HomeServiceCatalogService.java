package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeService;
import com.hospital.crm.homevisit.domain.HomeServiceRepository;
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
import java.util.UUID;

/**
 * 上门服务目录与审核服务：项目创建后为 draft，提交进入 pending_review，
 * 审核通过 published、驳回 rejected（必须带原因）；published 仅可修改说明，
 * 核心字段需停用后新建；published↔disabled 由管理权限切换，disabled 后不可下单。
 */
@Service
public class HomeServiceCatalogService {

    private static final String RESOURCE = "HOME_SERVICE";

    private final HomeServiceRepository serviceRepository;
    private final AuditService auditService;

    public HomeServiceCatalogService(HomeServiceRepository serviceRepository,
                                     AuditService auditService) {
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<HomeVisitDtos.HomeServiceView> list(String keyword,
                                                            String category,
                                                            String status,
                                                            int page,
                                                            int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HomeService> result = serviceRepository.findAll(
                buildSpecification(keyword, category, status), pageable);
        List<HomeVisitDtos.HomeServiceView> items = result.getContent().stream()
                .map(this::toView)
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public HomeVisitDtos.HomeServiceView create(HomeVisitDtos.HomeServiceCreateRequest request) {
        String serviceCode = request.serviceCode().trim().toUpperCase();
        if (serviceRepository.existsByServiceCode(serviceCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "服务编码已存在");
        }
        requireMedicalQualType(request.category(), request.requiredQualType());

        HomeService service = new HomeService();
        service.setServiceCode(serviceCode);
        service.setServiceName(request.serviceName().trim());
        service.setCategory(request.category());
        service.setRequiredQualType(trimToNull(request.requiredQualType()));
        service.setDurationMinutes(request.durationMinutes());
        service.setPrice(request.price());
        service.setStatus("draft");
        service.setDescription(trimToNull(request.description()));
        service = serviceRepository.save(service);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("serviceCode", service.getServiceCode());
        auditService.record("HOMESVC_CREATE", RESOURCE, service.getId().toString(), audit);

        return toView(service);
    }

    @Transactional
    public HomeVisitDtos.HomeServiceView update(String serviceCode,
                                                HomeVisitDtos.HomeServiceUpdateRequest request) {
        HomeService service = loadByCode(serviceCode);
        boolean coreChanged = hasCoreChange(service, request);
        if ("published".equals(service.getStatus())) {
            if (coreChanged) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                        "已发布服务不可修改核心字段，请停用后新建");
            }
        } else {
            if (request.serviceName() != null && !request.serviceName().isBlank()) {
                service.setServiceName(request.serviceName().trim());
            }
            if (request.category() != null && !request.category().isBlank()) {
                service.setCategory(request.category());
            }
            if (request.requiredQualType() != null) {
                service.setRequiredQualType(trimToNull(request.requiredQualType()));
            }
            if (request.durationMinutes() != null) {
                service.setDurationMinutes(request.durationMinutes());
            }
            if (request.price() != null) {
                service.setPrice(request.price());
            }
            requireMedicalQualType(service.getCategory(), service.getRequiredQualType());
        }
        if (request.description() != null) {
            service.setDescription(trimToNull(request.description()));
        }
        service = serviceRepository.save(service);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("serviceCode", service.getServiceCode());
        auditService.record("HOMESVC_UPDATE", RESOURCE, service.getId().toString(), audit);

        return toView(service);
    }

    @Transactional
    public HomeVisitDtos.HomeServiceView submit(String serviceCode) {
        HomeService service = loadByCode(serviceCode);
        if (!"draft".equals(service.getStatus()) && !"rejected".equals(service.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "仅草稿或已驳回的服务可提交审核");
        }
        service.setStatus("pending_review");
        service.setReviewReason(null);
        service = serviceRepository.save(service);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("serviceCode", service.getServiceCode());
        auditService.record("HOMESVC_SUBMIT", RESOURCE, service.getId().toString(), audit);

        return toView(service);
    }

    @Transactional
    public HomeVisitDtos.HomeServiceView review(String serviceCode,
                                                HomeVisitDtos.HomeServiceReviewRequest request) {
        HomeService service = loadByCode(serviceCode);
        if (!"pending_review".equals(service.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "仅待审核的服务可以审核");
        }
        String reason = trimToNull(request.reason());
        if (!request.approved() && reason == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "驳回服务必须填写原因");
        }
        service.setStatus(request.approved() ? "published" : "rejected");
        service.setReviewReason(request.approved() ? null : reason);
        service.setReviewedBy(CurrentUser.principal().username());
        service.setReviewedAt(Instant.now());
        service = serviceRepository.save(service);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("serviceCode", service.getServiceCode());
        audit.put("approved", request.approved());
        auditService.record("HOMESVC_REVIEW", RESOURCE, service.getId().toString(), audit);

        return toView(service);
    }

    @Transactional
    public HomeVisitDtos.HomeServiceView changeStatus(String serviceCode,
                                                      HomeVisitDtos.HomeServiceStatusRequest request) {
        HomeService service = loadByCode(serviceCode);
        if (!"published".equals(service.getStatus()) && !"disabled".equals(service.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "仅已发布或已停用的服务可以切换状态");
        }
        if (service.getStatus().equals(request.status())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "服务状态未变化");
        }
        service.setStatus(request.status());
        service = serviceRepository.save(service);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("serviceCode", service.getServiceCode());
        audit.put("status", service.getStatus());
        auditService.record("HOMESVC_STATUS", RESOURCE, service.getId().toString(), audit);

        return toView(service);
    }

    @Transactional(readOnly = true)
    public HomeService loadByCode(String serviceCode) {
        return serviceRepository.findByServiceCode(serviceCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "上门服务项不存在"));
    }

    /**
     * medical 类项目必须登记资质要求；non_medical 可空。
     */
    private void requireMedicalQualType(String category, String requiredQualType) {
        if ("medical".equals(category) && (requiredQualType == null || requiredQualType.isBlank())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "医疗服务项目必须登记资质要求");
        }
    }

    /**
     * 判断更新请求是否触及核心字段（名称、类别、资质要求、时长、价格）。
     */
    private boolean hasCoreChange(HomeService service, HomeVisitDtos.HomeServiceUpdateRequest request) {
        if (request.serviceName() != null && !request.serviceName().isBlank()
                && !request.serviceName().trim().equals(service.getServiceName())) {
            return true;
        }
        if (request.category() != null && !request.category().isBlank()
                && !request.category().equals(service.getCategory())) {
            return true;
        }
        if (request.requiredQualType() != null
                && !equalsTrimmed(request.requiredQualType(), service.getRequiredQualType())) {
            return true;
        }
        if (request.durationMinutes() != null && request.durationMinutes() != service.getDurationMinutes()) {
            return true;
        }
        return request.price() != null
                && (service.getPrice() == null || request.price().compareTo(service.getPrice()) != 0);
    }

    private boolean equalsTrimmed(String left, String right) {
        String a = trimToNull(left);
        return a == null ? right == null : a.equals(right);
    }

    private HomeVisitDtos.HomeServiceView toView(HomeService service) {
        return new HomeVisitDtos.HomeServiceView(
                service.getId(),
                service.getServiceCode(),
                service.getServiceName(),
                service.getCategory(),
                service.getRequiredQualType(),
                service.getDurationMinutes(),
                service.getPrice() == null ? null : service.getPrice().toPlainString(),
                service.getStatus(),
                service.getDescription(),
                service.getReviewReason(),
                service.getReviewedBy(),
                service.getReviewedAt());
    }

    private Specification<HomeService> buildSpecification(String keyword, String category, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("serviceCode")), like),
                        cb.like(cb.lower(root.get("serviceName")), like)));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
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
