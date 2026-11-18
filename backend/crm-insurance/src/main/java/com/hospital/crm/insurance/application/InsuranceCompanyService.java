package com.hospital.crm.insurance.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.insurance.api.InsuranceDtos;
import com.hospital.crm.insurance.domain.InsuranceCompany;
import com.hospital.crm.insurance.domain.InsuranceCompanyRepository;
import com.hospital.crm.insurance.domain.InsurancePolicy;
import com.hospital.crm.insurance.domain.InsurancePolicyRepository;
import com.hospital.crm.system.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 商保主数据服务：保险公司与保单的查询、维护与启停。
 *
 * <p>公司联系人手机号仅保存 HMAC 哈希与前 3 后 4 脱敏值，明文不落库；
 * 停用公司不得新建保单，保单仅 active 且未过期时方可建案。</p>
 */
@Service
public class InsuranceCompanyService {

    private static final String COMPANY_RESOURCE = "INSURANCE_COMPANY";
    private static final String POLICY_RESOURCE = "INSURANCE_POLICY";
    private static final Set<String> SETTLEMENT_TYPES = Set.of("api", "email", "manual");
    private static final Set<String> COMPANY_STATUSES = Set.of("active", "disabled");
    private static final Set<String> POLICY_STATUSES = Set.of("active", "expired", "terminated");
    private static final Set<String> RELATIONS = Set.of("self", "spouse", "child", "parent", "other");

    private final InsuranceCompanyRepository companyRepository;
    private final InsurancePolicyRepository policyRepository;
    private final CustomerService customerService;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public InsuranceCompanyService(InsuranceCompanyRepository companyRepository,
                                   InsurancePolicyRepository policyRepository,
                                   CustomerService customerService,
                                   SensitiveDataHasher hasher,
                                   AuditService auditService) {
        this.companyRepository = companyRepository;
        this.policyRepository = policyRepository;
        this.customerService = customerService;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 保险公司
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<InsuranceDtos.CompanyView> listCompanies(String keyword, String status,
                                                                 int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InsuranceCompany> result = companyRepository.findAll(
                companySpecification(keyword, status), pageable);
        List<InsuranceDtos.CompanyView> views = result.getContent().stream()
                .map(this::toCompanyView).toList();
        return PageResponse.of(views, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public InsuranceDtos.CompanyView companyDetail(UUID companyId) {
        return toCompanyView(requireCompany(companyId));
    }

    @Transactional
    public InsuranceDtos.CompanyView createCompany(InsuranceDtos.CreateCompanyRequest request) {
        String companyCode = request.companyCode() == null ? null : request.companyCode().trim();
        if (companyRepository.existsByCompanyCode(companyCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "保险公司编码已存在");
        }
        String settlementType = request.settlementType() == null || request.settlementType().isBlank()
                ? "manual" : request.settlementType();
        if (!SETTLEMENT_TYPES.contains(settlementType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算方式取值不合法");
        }

        InsuranceCompany company = new InsuranceCompany();
        company.setCompanyCode(companyCode);
        company.setCompanyName(request.companyName());
        company.setContactName(blankToNull(request.contactName()));
        applyContactPhone(company, request.contactPhone());
        company.setContactEmail(blankToNull(request.contactEmail()));
        company.setSettlementType(settlementType);
        company.setStatus("active");
        company.setRemark(blankToNull(request.remark()));
        InsuranceCompany saved = companyRepository.save(company);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("companyCode", saved.getCompanyCode());
        audit.put("settlementType", saved.getSettlementType());
        auditService.record("INSURANCE_COMPANY_CREATE", COMPANY_RESOURCE,
                saved.getId().toString(), audit);
        return toCompanyView(saved);
    }

    @Transactional
    public InsuranceDtos.CompanyView updateCompany(UUID companyId,
                                                   InsuranceDtos.UpdateCompanyRequest request) {
        InsuranceCompany company = requireCompany(companyId);
        if (request.companyName() != null && !request.companyName().isBlank()) {
            company.setCompanyName(request.companyName());
        }
        if (request.contactName() != null) {
            company.setContactName(blankToNull(request.contactName()));
        }
        if (request.contactPhone() != null) {
            applyContactPhone(company, request.contactPhone());
        }
        if (request.contactEmail() != null) {
            company.setContactEmail(blankToNull(request.contactEmail()));
        }
        if (request.settlementType() != null && !request.settlementType().isBlank()) {
            if (!SETTLEMENT_TYPES.contains(request.settlementType())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算方式取值不合法");
            }
            company.setSettlementType(request.settlementType());
        }
        if (request.remark() != null) {
            company.setRemark(blankToNull(request.remark()));
        }
        InsuranceCompany saved = companyRepository.save(company);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("companyCode", saved.getCompanyCode());
        auditService.record("INSURANCE_COMPANY_UPDATE", COMPANY_RESOURCE,
                saved.getId().toString(), audit);
        return toCompanyView(saved);
    }

    @Transactional
    public InsuranceDtos.CompanyView updateCompanyStatus(UUID companyId, String status) {
        if (!COMPANY_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "公司状态取值不合法");
        }
        InsuranceCompany company = requireCompany(companyId);
        companyRepository.updateStatus(companyId, status, Instant.now());
        InsuranceCompany refreshed = requireCompany(companyId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("companyCode", company.getCompanyCode());
        audit.put("status", status);
        auditService.record("INSURANCE_COMPANY_STATUS", COMPANY_RESOURCE, companyId.toString(), audit);
        return toCompanyView(refreshed);
    }

    // ------------------------------------------------------------------
    // 保单
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<InsuranceDtos.PolicyView> listPolicies(String keyword, UUID companyId,
                                                               UUID customerId, String status,
                                                               int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InsurancePolicy> result = policyRepository.findAll(
                policySpecification(keyword, companyId, customerId, status), pageable);
        return PageResponse.of(toPolicyViews(result.getContent()), result.getTotalElements(),
                safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public InsuranceDtos.PolicyView policyDetail(UUID policyId) {
        return toPolicyViews(List.of(requirePolicy(policyId))).get(0);
    }

    @Transactional
    public InsuranceDtos.PolicyView createPolicy(InsuranceDtos.CreatePolicyRequest request) {
        InsuranceCompany company = requireCompany(request.companyId());
        if (!"active".equals(company.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "保险公司已停用，不能新建保单");
        }
        String policyNo = request.policyNo() == null ? null : request.policyNo().trim();
        if (policyRepository.existsByPolicyNo(policyNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "保单号已存在");
        }
        CustomerDtos.CustomerBrief customer = customerService.brief(request.customerId());
        if (request.validFrom() != null && request.validTo() != null
                && request.validFrom().isAfter(request.validTo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "保单生效日期不能晚于失效日期");
        }
        String relation = request.relation() == null || request.relation().isBlank()
                ? "self" : request.relation();
        if (!RELATIONS.contains(relation)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "与被保人关系取值不合法");
        }
        BigDecimal coverage = request.coverageAmount() == null
                ? BigDecimal.ZERO : request.coverageAmount();
        if (coverage.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "保障额度不能为负数");
        }

        InsurancePolicy policy = new InsurancePolicy();
        policy.setPolicyNo(policyNo);
        policy.setCompanyId(company.getId());
        policy.setCustomerId(customer.id());
        policy.setHolderName(blankToNull(request.holderName()));
        policy.setInsuredName(blankToNull(request.insuredName()));
        policy.setRelation(relation);
        policy.setValidFrom(request.validFrom());
        policy.setValidTo(request.validTo());
        policy.setCoverageAmount(coverage);
        policy.setStatus("active");
        policy.setRemark(blankToNull(request.remark()));
        InsurancePolicy saved = policyRepository.save(policy);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("policyNo", saved.getPolicyNo());
        audit.put("companyId", saved.getCompanyId().toString());
        auditService.record("INSURANCE_POLICY_CREATE", POLICY_RESOURCE, saved.getId().toString(), audit);
        return toPolicyViews(List.of(saved)).get(0);
    }

    @Transactional
    public InsuranceDtos.PolicyView updatePolicyStatus(UUID policyId, String status) {
        if (!POLICY_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "保单状态取值不合法");
        }
        InsurancePolicy policy = requirePolicy(policyId);
        policyRepository.updateStatus(policyId, status, Instant.now());
        InsurancePolicy refreshed = requirePolicy(policyId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("policyNo", policy.getPolicyNo());
        audit.put("status", status);
        auditService.record("INSURANCE_POLICY_STATUS", POLICY_RESOURCE, policyId.toString(), audit);
        return toPolicyViews(List.of(refreshed)).get(0);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    InsuranceCompany requireCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "保险公司不存在"));
    }

    InsurancePolicy requirePolicy(UUID policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "保单不存在"));
    }

    private void applyContactPhone(InsuranceCompany company, String contactPhone) {
        String normalized = CustomerMasking.normalizePhone(contactPhone);
        if (normalized == null) {
            company.setContactPhoneHash(null);
            company.setContactPhoneMasked(null);
            return;
        }
        company.setContactPhoneHash(hasher.hash(normalized));
        company.setContactPhoneMasked(CustomerMasking.maskPhone(normalized));
    }

    private Specification<InsuranceCompany> companySpecification(String keyword, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("companyCode")), like),
                        cb.like(cb.lower(root.get("companyName")), like)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<InsurancePolicy> policySpecification(String keyword, UUID companyId,
                                                               UUID customerId, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("policyNo")), like));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private InsuranceDtos.CompanyView toCompanyView(InsuranceCompany company) {
        return new InsuranceDtos.CompanyView(
                company.getId(),
                company.getCompanyCode(),
                company.getCompanyName(),
                company.getContactName(),
                company.getContactPhoneMasked(),
                company.getContactEmail(),
                company.getSettlementType(),
                company.getStatus(),
                company.getRemark(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private List<InsuranceDtos.PolicyView> toPolicyViews(List<InsurancePolicy> policies) {
        if (policies.isEmpty()) {
            return List.of();
        }
        Set<UUID> companyIds = policies.stream().map(InsurancePolicy::getCompanyId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Set<UUID> customerIds = policies.stream().map(InsurancePolicy::getCustomerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<UUID, String> companyNames = companyIds.isEmpty() ? Map.of()
                : companyRepository.findAllById(companyIds).stream()
                        .collect(Collectors.toMap(InsuranceCompany::getId, InsuranceCompany::getCompanyName));
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerIds.isEmpty() ? Map.of()
                : customerService.briefs(customerIds);
        return policies.stream().map(policy -> {
            CustomerDtos.CustomerBrief customer = customers.get(policy.getCustomerId());
            return new InsuranceDtos.PolicyView(
                    policy.getId(),
                    policy.getPolicyNo(),
                    policy.getCompanyId(),
                    companyNames.get(policy.getCompanyId()),
                    policy.getCustomerId(),
                    customer == null ? null : customer.name(),
                    customer == null ? null : customer.customerNo(),
                    policy.getHolderName(),
                    policy.getInsuredName(),
                    policy.getRelation(),
                    policy.getValidFrom(),
                    policy.getValidTo(),
                    policy.getCoverageAmount(),
                    policy.getStatus(),
                    policy.getRemark(),
                    policy.getCreatedAt(),
                    policy.getUpdatedAt());
        }).toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
