package com.hospital.crm.customer.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.domain.Customer;
import com.hospital.crm.customer.domain.CustomerRepository;
import com.hospital.crm.customer.domain.Tag;
import com.hospital.crm.customer.domain.TagRepository;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerService {

    private static final String RESOURCE_TYPE = "CUSTOMER";
    private static final String SENSITIVE_PERMISSION = "crm:customer:sensitive:read";

    private final CustomerRepository customerRepository;
    private final TagRepository tagRepository;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository,
                           TagRepository tagRepository,
                           SensitiveDataCipher cipher,
                           SensitiveDataHasher hasher,
                           AuditService auditService) {
        this.customerRepository = customerRepository;
        this.tagRepository = tagRepository;
        this.cipher = cipher;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDtos.CustomerSummary> list(String keyword,
                                                           UUID tagId,
                                                           String sourceCode,
                                                           String status,
                                                           String gender,
                                                           int page,
                                                           int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Customer> specification = buildSpecification(keyword, tagId, sourceCode, status, gender);
        Page<Customer> result = customerRepository.findAll(specification, pageable);
        List<CustomerDtos.CustomerSummary> items = result.getContent().stream()
                .map(this::toSummary)
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public CustomerDtos.CustomerDetail detail(UUID customerId) {
        Customer customer = load(customerId);
        boolean sensitiveVisible = CurrentUser.hasPermission(SENSITIVE_PERMISSION);
        String phone = null;
        String idNo = null;
        if (sensitiveVisible) {
            phone = cipher.decrypt(customer.getPhoneEncrypted());
            idNo = cipher.decrypt(customer.getIdNoEncrypted());
            if (phone != null || idNo != null) {
                auditService.record("CUSTOMER_SENSITIVE_VIEW", RESOURCE_TYPE, customerId.toString(),
                        Map.of("customerNo", customer.getCustomerNo()));
            }
        }
        return new CustomerDtos.CustomerDetail(
                customer.getId(),
                customer.getCustomerNo(),
                customer.getName(),
                customer.getGender(),
                customer.getBirthDate(),
                customer.getPhoneMasked(),
                customer.getIdNoMasked(),
                phone,
                idNo,
                sensitiveVisible,
                customer.getSourceCode(),
                customer.getStatus(),
                customer.getNotes(),
                tagSummaries(customer),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public CustomerDtos.CustomerBrief brief(UUID customerId) {
        return toBrief(load(customerId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, CustomerDtos.CustomerBrief> briefs(java.util.Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findAllById(customerIds).stream()
                .collect(java.util.stream.Collectors.toMap(Customer::getId, this::toBrief));
    }

    @Transactional
    public CustomerDtos.CustomerSummary create(CustomerDtos.CreateRequest request) {
        String phone = validatePhone(request.phone());
        String idNo = validateIdNo(request.idNo());
        boolean forceCreate = Boolean.TRUE.equals(request.forceCreate());
        ensureNoDuplicate(phone, idNo, null, forceCreate);

        Set<Tag> tags = resolveTags(request.tagIds());
        Customer customer = new Customer();
        customer.setCustomerNo(nextCustomerNo());
        customer.setName(request.name().trim());
        customer.setGender(request.gender() == null ? "unknown" : request.gender());
        customer.setBirthDate(request.birthDate());
        applyPhone(customer, phone);
        applyIdNo(customer, idNo);
        customer.setSourceCode(blankToNull(request.sourceCode()) == null ? "manual" : request.sourceCode().trim());
        customer.setNotes(blankToNull(request.notes()));
        customer.setStatus("active");
        customer.setTags(tags);
        customer = customerRepository.save(customer);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("customerNo", customer.getCustomerNo());
        detail.put("name", customer.getName());
        detail.put("sourceCode", customer.getSourceCode());
        detail.put("tags", tagCodes(customer));
        auditService.record("CUSTOMER_CREATE", RESOURCE_TYPE, customer.getId().toString(), detail);

        return toSummary(customer);
    }

    @Transactional
    public CustomerDtos.CustomerSummary update(UUID customerId, CustomerDtos.UpdateRequest request) {
        Customer customer = load(customerId);
        if (!"active".equals(customer.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅启用中的客户档案允许编辑");
        }

        List<String> changed = new ArrayList<>();
        if (request.name() != null && !request.name().isBlank()
                && !request.name().trim().equals(customer.getName())) {
            customer.setName(request.name().trim());
            changed.add("name");
        }
        if (request.gender() != null && !request.gender().equals(customer.getGender())) {
            customer.setGender(request.gender());
            changed.add("gender");
        }
        if (request.birthDate() != null && !request.birthDate().equals(customer.getBirthDate())) {
            customer.setBirthDate(request.birthDate());
            changed.add("birthDate");
        }
        if (request.phone() != null) {
            String phone = validatePhone(request.phone());
            String newHash = hasher.hash(phone);
            if (!java.util.Objects.equals(newHash, customer.getPhoneHash())) {
                ensureNoDuplicate(phone, null, customerId, false);
                applyPhone(customer, phone);
                changed.add("phone");
            }
        }
        if (request.idNo() != null) {
            String idNo = validateIdNo(request.idNo());
            String newHash = hasher.hash(idNo);
            if (!java.util.Objects.equals(newHash, customer.getIdNoHash())) {
                ensureNoDuplicate(null, idNo, customerId, false);
                applyIdNo(customer, idNo);
                changed.add("idNo");
            }
        }
        if (request.sourceCode() != null) {
            String sourceCode = blankToNull(request.sourceCode());
            if (!java.util.Objects.equals(sourceCode, customer.getSourceCode())) {
                customer.setSourceCode(sourceCode);
                changed.add("sourceCode");
            }
        }
        if (request.notes() != null) {
            String notes = blankToNull(request.notes());
            if (!java.util.Objects.equals(notes, customer.getNotes())) {
                customer.setNotes(notes);
                changed.add("notes");
            }
        }
        if (request.tagIds() != null) {
            customer.setTags(resolveTags(request.tagIds()));
            changed.add("tags");
        }
        customer = customerRepository.saveAndFlush(customer);

        if (!changed.isEmpty()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("customerNo", customer.getCustomerNo());
            detail.put("changedFields", changed);
            detail.put("tags", tagCodes(customer));
            auditService.record("CUSTOMER_UPDATE", RESOURCE_TYPE, customerId.toString(), detail);
        }
        return toSummary(customer);
    }

    @Transactional
    public void changeStatus(UUID customerId, String status) {
        Customer customer = load(customerId);
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态取值不合法");
        }
        if (customer.getStatus().equals(status)) {
            return;
        }
        customer.setStatus(status);
        customerRepository.save(customer);
        auditService.record("CUSTOMER_STATUS_CHANGED", RESOURCE_TYPE, customerId.toString(),
                Map.of("customerNo", customer.getCustomerNo(), "status", status));
    }

    private Specification<Customer> buildSpecification(String keyword,
                                                       UUID tagId,
                                                       String sourceCode,
                                                       String status,
                                                       String gender) {
        String trimmedKeyword = blankToNull(keyword);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (gender != null && !gender.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("gender"), gender));
            }
            if (sourceCode != null && !sourceCode.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("sourceCode"), sourceCode));
            }
            if (trimmedKeyword != null) {
                String digits = CustomerMasking.normalizePhone(trimmedKeyword);
                if (digits != null && digits.matches("^1\\d{10}$")) {
                    predicates.add(criteriaBuilder.equal(root.get("phoneHash"), hasher.hash(digits)));
                } else if (isIdNo(trimmedKeyword)) {
                    predicates.add(criteriaBuilder.equal(root.get("idNoHash"),
                            hasher.hash(CustomerMasking.normalizeIdNo(trimmedKeyword))));
                } else {
                    String like = "%" + escapeLike(trimmedKeyword.toLowerCase()) + "%";
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), like, '\\'),
                            criteriaBuilder.like(criteriaBuilder.lower(root.get("customerNo")), like, '\\')));
                }
            }
            if (tagId != null) {
                predicates.add(root.join("tags", JoinType.INNER).get("id").in(tagId));
                if (query != null) {
                    query.distinct(true);
                }
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Customer load(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "客户档案不存在"));
    }

    private void ensureNoDuplicate(String phone, String idNo, UUID excludeId, boolean forceCreate) {
        if (forceCreate) {
            return;
        }
        if (phone != null) {
            customerRepository.findFirstByPhoneHashAndStatus(hasher.hash(phone), "active")
                    .filter(existing -> !existing.getId().equals(excludeId))
                    .ifPresent(existing -> {
                        throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                                "手机号已存在客户档案（" + existing.getCustomerNo() + "），如确认非同一人请勾选强制创建");
                    });
        }
        if (idNo != null) {
            customerRepository.findFirstByIdNoHashAndStatus(hasher.hash(idNo), "active")
                    .filter(existing -> !existing.getId().equals(excludeId))
                    .ifPresent(existing -> {
                        throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                                "证件号已存在客户档案（" + existing.getCustomerNo() + "），请先核验是否为重复客户");
                    });
        }
    }

    private Set<Tag> resolveTags(List<UUID> tagIds) {
        if (tagIds == null) {
            return new HashSet<>();
        }
        if (tagIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<UUID> uniqueIds = new HashSet<>(tagIds);
        List<Tag> found = tagRepository.findAllById(uniqueIds).stream()
                .filter(tag -> "active".equals(tag.getStatus()))
                .toList();
        if (found.size() != uniqueIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "包含不存在或已停用的标签");
        }
        return new HashSet<>(found);
    }

    private void applyPhone(Customer customer, String phone) {
        customer.setPhoneEncrypted(cipher.encrypt(phone));
        customer.setPhoneHash(hasher.hash(phone));
        customer.setPhoneMasked(CustomerMasking.maskPhone(phone));
    }

    private void applyIdNo(Customer customer, String idNo) {
        customer.setIdNoEncrypted(cipher.encrypt(idNo));
        customer.setIdNoHash(hasher.hash(idNo));
        customer.setIdNoMasked(CustomerMasking.maskIdNo(idNo));
    }

    private String nextCustomerNo() {
        Long sequence = customerRepository.nextCustomerNo();
        return "C" + (sequence == null ? "" : sequence);
    }

    private String validatePhone(String raw) {
        String normalized = CustomerMasking.normalizePhone(raw);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号需为 11 位且以 1 开头");
        }
        return normalized;
    }

    private String validateIdNo(String raw) {
        String normalized = CustomerMasking.normalizeIdNo(raw);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("^(\\d{15}|\\d{17}[\\dX])$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "证件号需为 15 位或 18 位身份证格式");
        }
        return normalized;
    }

    private boolean isIdNo(String value) {
        String normalized = CustomerMasking.normalizeIdNo(value);
        return normalized != null && normalized.matches("^(\\d{15}|\\d{17}[\\dX])$");
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<CustomerDtos.TagSummary> tagSummaries(Customer customer) {
        return customer.getTags().stream()
                .sorted(Comparator.comparingInt(Tag::getSortOrder).thenComparing(Tag::getTagName))
                .map(tag -> new CustomerDtos.TagSummary(tag.getId(), tag.getTagCode(), tag.getTagName(), tag.getTagType()))
                .toList();
    }

    private List<String> tagCodes(Customer customer) {
        return customer.getTags().stream().map(Tag::getTagCode).sorted().toList();
    }

    private CustomerDtos.CustomerBrief toBrief(Customer customer) {
        return new CustomerDtos.CustomerBrief(
                customer.getId(),
                customer.getCustomerNo(),
                customer.getName(),
                customer.getPhoneMasked(),
                customer.getStatus()
        );
    }

    private CustomerDtos.CustomerSummary toSummary(Customer customer) {
        return new CustomerDtos.CustomerSummary(
                customer.getId(),
                customer.getCustomerNo(),
                customer.getName(),
                customer.getGender(),
                customer.getBirthDate(),
                customer.getPhoneMasked(),
                customer.getIdNoMasked(),
                customer.getSourceCode(),
                customer.getStatus(),
                tagSummaries(customer),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
