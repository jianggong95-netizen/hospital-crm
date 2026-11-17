package com.hospital.crm.operation.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.operation.api.OperationDtos;
import com.hospital.crm.operation.domain.Lead;
import com.hospital.crm.operation.domain.LeadAssignment;
import com.hospital.crm.operation.domain.LeadAssignmentRepository;
import com.hospital.crm.operation.domain.LeadContactRecord;
import com.hospital.crm.operation.domain.LeadContactRecordRepository;
import com.hospital.crm.operation.domain.LeadConversion;
import com.hospital.crm.operation.domain.LeadConversionRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 线索服务：重复核验、联系记录、分派、合并与转客户追溯。
 * 线索敏感字段只返回脱敏值；转客户时在服务内部解密并交由客户中心重新加密落库。
 */
@Service
public class LeadService {

    private static final String RESOURCE = "LEAD";
    private static final Set<String> DUPLICATE_ACTIVE_STATUSES = Set.of("NEW", "CONTACTING", "QUALIFIED", "INVITED", "CONVERTED");
    private static final Set<String> TERMINAL_STATUSES = Set.of("CONVERTED", "MERGED", "INVALID");

    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "NEW", Set.of("CONTACTING", "INVALID"),
            "CONTACTING", Set.of("QUALIFIED", "INVALID"),
            "QUALIFIED", Set.of("INVITED", "INVALID"),
            "INVITED", Set.of("QUALIFIED", "INVALID")
    );

    private static final Map<String, String> CUSTOMER_SOURCE_MAPPING = Map.of(
            "ONLINE", "online",
            "REFERRAL", "referral",
            "ACTIVITY", "activity",
            "HIS", "outpatient",
            "WALK_IN", "manual",
            "PHONE", "manual",
            "MANUAL", "manual"
    );

    private final LeadRepository leadRepository;
    private final LeadContactRecordRepository contactRepository;
    private final LeadAssignmentRepository assignmentRepository;
    private final LeadConversionRepository conversionRepository;
    private final SourceChannelRepository channelRepository;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public LeadService(LeadRepository leadRepository,
                       LeadContactRecordRepository contactRepository,
                       LeadAssignmentRepository assignmentRepository,
                       LeadConversionRepository conversionRepository,
                       SourceChannelRepository channelRepository,
                       CustomerService customerService,
                       SysUserRepository userRepository,
                       SensitiveDataCipher cipher,
                       SensitiveDataHasher hasher,
                       AuditService auditService) {
        this.leadRepository = leadRepository;
        this.contactRepository = contactRepository;
        this.assignmentRepository = assignmentRepository;
        this.conversionRepository = conversionRepository;
        this.channelRepository = channelRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.cipher = cipher;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationDtos.LeadSummary> list(String keyword,
                                                        String status,
                                                        String channelCode,
                                                        UUID ownerUserId,
                                                        int page,
                                                        int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Lead> result = leadRepository.findAll(buildSpecification(keyword, status, channelCode, ownerUserId),
                pageable);
        Map<String, String> channelNames = channelNames();
        Map<UUID, String> ownerNames = userNames(result.getContent().stream()
                .map(Lead::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<OperationDtos.LeadSummary> items = result.getContent().stream()
                .map(lead -> toSummary(lead, channelNames, ownerNames))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public OperationDtos.LeadDetail detail(UUID leadId) {
        Lead lead = load(leadId);
        Map<String, String> channelNames = channelNames();
        Set<UUID> userIds = new LinkedHashSet<>();
        if (lead.getOwnerUserId() != null) {
            userIds.add(lead.getOwnerUserId());
        }
        List<LeadContactRecord> contacts = contactRepository.findByLeadIdOrderByContactedAtDesc(leadId)
                .stream().limit(20).toList();
        contacts.stream().map(LeadContactRecord::getContactedBy).filter(Objects::nonNull).forEach(userIds::add);
        List<LeadAssignment> assignments = assignmentRepository.findByLeadIdOrderByAssignedAtDesc(leadId);
        assignments.forEach(item -> {
            if (item.getFromUserId() != null) {
                userIds.add(item.getFromUserId());
            }
            userIds.add(item.getToUserId());
        });
        Map<UUID, String> userNames = userNames(userIds);
        Optional<LeadConversion> conversion = conversionRepository.findByLeadId(leadId);
        conversion.ifPresent(item -> userIds.add(item.getConvertedBy()));

        return new OperationDtos.LeadDetail(
                toSummary(lead, channelNames, userNames),
                lead.getRemark(),
                lead.getConvertedAt(),
                contacts.stream().map(contact -> toContactView(contact, userNames)).toList(),
                assignments.stream().map(item -> toAssignmentView(item, userNames)).toList(),
                conversion.map(item -> toConversionView(item, userNames)).orElse(null));
    }

    @Transactional
    public OperationDtos.LeadSummary create(OperationDtos.LeadCreateRequest request) {
        String channelCode = requireChannel(request.sourceChannelCode());
        String phone = normalizePhone(request.phone());
        String idNo = normalizeIdNo(request.idNo());
        boolean forceCreate = Boolean.TRUE.equals(request.forceCreate());
        if (!forceCreate) {
            List<OperationDtos.DuplicateLead> duplicates = findDuplicates(phone, idNo, null, 5);
            if (!duplicates.isEmpty()) {
                String leadNos = duplicates.stream().map(OperationDtos.DuplicateLead::leadNo)
                        .collect(Collectors.joining("、"));
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                        "发现 " + duplicates.size() + " 条疑似重复线索（" + leadNos + "），请先核验并合并；"
                                + "确认非同一人时可勾选强制创建");
            }
        }

        Lead lead = new Lead();
        lead.setLeadNo("L" + leadRepository.nextLeadNo());
        lead.setName(request.name().trim());
        lead.setGender(request.gender() == null ? "unknown" : request.gender());
        lead.setBirthDate(request.birthDate());
        applyPhone(lead, phone);
        applyIdNo(lead, idNo);
        lead.setSourceChannelCode(channelCode);
        lead.setStatus("NEW");
        lead.setOwnerUserId(resolveOwner(request.ownerUserId()));
        lead.setOrgId(CurrentUser.principal().orgId());
        lead.setRemark(blankToNull(request.remark()));
        lead.setCreatedBy(CurrentUser.id());
        lead.setUpdatedBy(CurrentUser.id());
        lead = leadRepository.save(lead);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("leadNo", lead.getLeadNo());
        audit.put("name", lead.getName());
        audit.put("channelCode", lead.getSourceChannelCode());
        audit.put("ownerUserId", String.valueOf(lead.getOwnerUserId()));
        auditService.record("LEAD_CREATE", RESOURCE, lead.getId().toString(), audit);

        return toSummary(lead, channelNames(), userNames(ownerIds(lead)));
    }

    @Transactional
    public OperationDtos.LeadSummary update(UUID leadId, OperationDtos.LeadUpdateRequest request) {
        Lead lead = load(leadId);
        ensureNotTerminal(lead, "已转化、已合并或已作废的线索不允许编辑");

        List<String> changed = new ArrayList<>();
        if (request.name() != null && !request.name().isBlank() && !request.name().trim().equals(lead.getName())) {
            lead.setName(request.name().trim());
            changed.add("name");
        }
        if (request.gender() != null && !request.gender().equals(lead.getGender())) {
            lead.setGender(request.gender());
            changed.add("gender");
        }
        if (request.birthDate() != null) {
            lead.setBirthDate(request.birthDate());
            changed.add("birthDate");
        }
        if (request.phone() != null) {
            String phone = normalizePhone(request.phone());
            ensureNoDuplicate(phone, null, leadId);
            applyPhone(lead, phone);
            changed.add("phone");
        }
        if (request.idNo() != null) {
            String idNo = normalizeIdNo(request.idNo());
            ensureNoDuplicate(null, idNo, leadId);
            applyIdNo(lead, idNo);
            changed.add("idNo");
        }
        if (request.sourceChannelCode() != null) {
            lead.setSourceChannelCode(requireChannel(request.sourceChannelCode()));
            changed.add("sourceChannelCode");
        }
        if (request.ownerUserId() != null && !request.ownerUserId().equals(lead.getOwnerUserId())) {
            lead.setOwnerUserId(resolveOwner(request.ownerUserId()));
            changed.add("ownerUserId");
        }
        if (request.remark() != null) {
            lead.setRemark(blankToNull(request.remark()));
            changed.add("remark");
        }
        lead.setUpdatedBy(CurrentUser.id());
        lead = leadRepository.save(lead);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("leadNo", lead.getLeadNo());
        audit.put("changed", changed);
        auditService.record("LEAD_UPDATE", RESOURCE, leadId.toString(), audit);

        return toSummary(lead, channelNames(), userNames(ownerIds(lead)));
    }

    @Transactional
    public OperationDtos.LeadSummary changeStatus(UUID leadId, OperationDtos.LeadStatusRequest request) {
        Lead lead = load(leadId);
        String target = request.status().trim().toUpperCase();
        if ("CONVERTED".equals(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "请使用线索转客户接口完成转化");
        }
        if ("MERGED".equals(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "请使用线索合并接口完成合并");
        }
        if (TERMINAL_STATUSES.contains(lead.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "当前线索已处于终态，不能继续变更状态");
        }
        Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(lead.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "线索状态不允许从 " + lead.getStatus() + " 变更为 " + target);
        }
        String previous = lead.getStatus();
        lead.setStatus(target);
        if (request.remark() != null) {
            lead.setRemark(blankToNull(request.remark()));
        }
        lead.setUpdatedBy(CurrentUser.id());
        lead = leadRepository.save(lead);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("leadNo", lead.getLeadNo());
        audit.put("fromStatus", previous);
        audit.put("toStatus", target);
        auditService.record("LEAD_STATUS_CHANGE", RESOURCE, leadId.toString(), audit);
        return toSummary(lead, channelNames(), userNames(ownerIds(lead)));
    }

    @Transactional(readOnly = true)
    public List<OperationDtos.AssigneeView> assignees(String keyword) {
        Specification<SysUser> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "active"));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("displayName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SysUser> users = userRepository.findAll(specification,
                PageRequest.of(0, 20, Sort.by("displayName")));
        return users.getContent().stream()
                .map(user -> new OperationDtos.AssigneeView(
                        user.getId(), user.getUsername(), user.getDisplayName(), user.getOrgId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperationDtos.DuplicateLead> duplicates(String phone, String idNo) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedIdNo = normalizeIdNo(idNo);
        if (normalizedPhone == null && normalizedIdNo == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请提供手机号或证件号用于重复线索核验");
        }
        return findDuplicates(normalizedPhone, normalizedIdNo, null, 20);
    }

    @Transactional
    public OperationDtos.ConversionView convert(UUID leadId, OperationDtos.LeadConvertRequest request) {
        Lead lead = load(leadId);
        if (lead.getConvertedCustomerId() != null || conversionRepository.existsByLeadId(leadId)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该线索已转化，不能重复转化");
        }
        if ("MERGED".equals(lead.getStatus()) || "INVALID".equals(lead.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已合并或已作废的线索不能转化");
        }

        UUID customerId;
        String customerNo;
        String customerName;
        String conversionMode;
        if (request.customerId() != null) {
            CustomerDtos.CustomerDetail customer = customerService.detail(request.customerId());
            customerId = customer.id();
            customerNo = customer.customerNo();
            customerName = customer.name();
            conversionMode = "EXISTING_CUSTOMER";
        } else {
            if (Boolean.FALSE.equals(request.createCustomer())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择关联既有客户或允许创建新客户");
            }
            String phone = cipher.decrypt(lead.getPhoneEncrypted());
            String idNo = cipher.decrypt(lead.getIdNoEncrypted());
            if (phone == null && idNo == null) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                        "线索缺少手机号与证件号，无法创建客户档案，请补充后重试");
            }
            CustomerDtos.CreateRequest createRequest = new CustomerDtos.CreateRequest(
                    lead.getName(),
                    lead.getGender(),
                    lead.getBirthDate(),
                    phone,
                    idNo,
                    CUSTOMER_SOURCE_MAPPING.getOrDefault(lead.getSourceChannelCode(), "manual"),
                    "由线索 " + lead.getLeadNo() + " 转化",
                    List.of(),
                    false);
            try {
                CustomerDtos.CustomerSummary customer = customerService.create(createRequest);
                customerId = customer.id();
                customerNo = customer.customerNo();
                customerName = customer.name();
                conversionMode = "NEW_CUSTOMER";
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.BUSINESS_CONFLICT) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                            "客户档案已存在，请核验后选择关联既有客户：" + ex.getMessage());
                }
                throw ex;
            }
        }

        lead.setStatus("CONVERTED");
        lead.setConvertedCustomerId(customerId);
        lead.setConvertedAt(Instant.now());
        lead.setUpdatedBy(CurrentUser.id());
        lead = leadRepository.save(lead);

        LeadConversion conversion = new LeadConversion();
        conversion.setLeadId(leadId);
        conversion.setCustomerId(customerId);
        conversion.setConversionMode(conversionMode);
        conversion.setRemark(blankToNull(request.remark()));
        conversion.setConvertedBy(CurrentUser.id());
        conversion = conversionRepository.save(conversion);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("leadNo", lead.getLeadNo());
        audit.put("customerNo", customerNo);
        audit.put("conversionMode", conversionMode);
        auditService.record("LEAD_CONVERT", RESOURCE, leadId.toString(), audit);

        return toConversionView(conversion, customerNo, customerName, userNames(conversion.getConvertedBy()));
    }

    @Transactional
    public OperationDtos.LeadSummary merge(UUID leadId, OperationDtos.LeadMergeRequest request) {
        Lead source = load(leadId);
        Lead target = load(request.targetLeadId());
        if (source.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能将线索合并到自身");
        }
        if ("MERGED".equals(source.getStatus()) || "CONVERTED".equals(source.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已合并或已转化的线索不能再次合并");
        }
        if ("MERGED".equals(target.getStatus()) || "INVALID".equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "目标线索已合并或已作废，不能作为合并目标");
        }
        source.setStatus("MERGED");
        source.setMergedIntoLeadId(target.getId());
        source.setUpdatedBy(CurrentUser.id());
        source = leadRepository.save(source);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("sourceLeadNo", source.getLeadNo());
        audit.put("targetLeadNo", target.getLeadNo());
        audit.put("reason", blankToNull(request.reason()));
        auditService.record("LEAD_MERGE", RESOURCE, leadId.toString(), audit);
        return toSummary(source, channelNames(), userNames(ownerIds(source)));
    }

    @Transactional
    public OperationDtos.LeadSummary assign(UUID leadId, OperationDtos.LeadAssignRequest request) {
        Lead lead = load(leadId);
        if ("MERGED".equals(lead.getStatus()) || "INVALID".equals(lead.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已合并或已作废的线索不能分派");
        }
        SysUser target = userRepository.findById(request.toUserId())
                .filter(user -> "active".equals(user.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "目标员工不存在或已停用"));
        UUID fromUserId = lead.getOwnerUserId();
        if (target.getId().equals(fromUserId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "目标员工已是当前负责人");
        }

        LeadAssignment assignment = new LeadAssignment();
        assignment.setLeadId(leadId);
        assignment.setFromUserId(fromUserId);
        assignment.setToUserId(target.getId());
        assignment.setReason(blankToNull(request.reason()));
        assignment.setAssignedBy(CurrentUser.id());
        assignmentRepository.save(assignment);

        lead.setOwnerUserId(target.getId());
        lead.setUpdatedBy(CurrentUser.id());
        lead = leadRepository.save(lead);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("leadNo", lead.getLeadNo());
        audit.put("fromUserId", fromUserId == null ? "" : fromUserId.toString());
        audit.put("toUserId", target.getId().toString());
        auditService.record("LEAD_ASSIGN", RESOURCE, leadId.toString(), audit);

        return toSummary(lead, channelNames(), userNames(ownerIds(lead)));
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationDtos.ContactRecordView> contacts(UUID leadId, int page, int pageSize) {
        load(leadId);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<LeadContactRecord> result = contactRepository.findByLeadIdOrderByContactedAtDesc(
                leadId, PageRequest.of(safePage - 1, safePageSize));
        Map<UUID, String> userNames = userNames(result.getContent().stream()
                .map(LeadContactRecord::getContactedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return PageResponse.of(result.getContent().stream()
                        .map(contact -> toContactView(contact, userNames)).toList(),
                result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public OperationDtos.ContactRecordView addContact(UUID leadId, OperationDtos.ContactRecordCreateRequest request) {
        Lead lead = load(leadId);
        if ("MERGED".equals(lead.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已合并线索不允许新增联系记录");
        }
        LeadContactRecord record = new LeadContactRecord();
        record.setLeadId(leadId);
        record.setContactType(request.contactType());
        record.setContactedAt(request.contactedAt() == null ? Instant.now() : request.contactedAt());
        record.setContactedBy(CurrentUser.id());
        record.setContent(request.content().trim());
        record.setResultCode(request.resultCode());
        record.setNextFollowUpAt(request.nextFollowUpAt());
        record = contactRepository.save(record);

        if ("NEW".equals(lead.getStatus())) {
            lead.setStatus("CONTACTING");
            lead.setUpdatedBy(CurrentUser.id());
            leadRepository.save(lead);
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("leadNo", lead.getLeadNo());
        audit.put("contactType", record.getContactType());
        audit.put("resultCode", record.getResultCode());
        auditService.record("LEAD_CONTACT_CREATE", RESOURCE, leadId.toString(), audit);

        return toContactView(record, userNames(record.getContactedBy()));
    }

    @Transactional(readOnly = true)
    public OperationDtos.ConversionView conversion(UUID leadId) {
        load(leadId);
        LeadConversion conversion = conversionRepository.findByLeadId(leadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该线索尚无转化记录"));
        CustomerDtos.CustomerBrief customer = customerService.brief(conversion.getCustomerId());
        return toConversionView(conversion, customer.customerNo(), customer.name(),
                userNames(conversion.getConvertedBy()));
    }

    @Transactional(readOnly = true)
    public List<OperationDtos.AssignmentView> assignments(UUID leadId) {
        load(leadId);
        List<LeadAssignment> assignments = assignmentRepository.findByLeadIdOrderByAssignedAtDesc(leadId);
        Set<UUID> userIds = new HashSet<>();
        assignments.forEach(item -> {
            if (item.getFromUserId() != null) {
                userIds.add(item.getFromUserId());
            }
            userIds.add(item.getToUserId());
        });
        Map<UUID, String> names = userNames(userIds);
        return assignments.stream().map(item -> toAssignmentView(item, names)).toList();
    }

    private Specification<Lead> buildSpecification(String keyword, String status, String channelCode, UUID owner) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (channelCode != null && !channelCode.isBlank()) {
                predicates.add(cb.equal(root.get("sourceChannelCode"), channelCode.trim().toUpperCase()));
            }
            if (owner != null) {
                predicates.add(cb.equal(root.get("ownerUserId"), owner));
            }
            if (keyword != null && !keyword.isBlank()) {
                String trimmed = keyword.trim();
                String like = "%" + trimmed.toLowerCase() + "%";
                Predicate textMatch = cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("leadNo")), like),
                        cb.like(cb.lower(root.get("phoneMasked")), like));
                String phone = CustomerMasking.normalizePhone(trimmed);
                if (phone != null && phone.matches("^1\\d{10}$")) {
                    textMatch = cb.or(textMatch, cb.equal(root.get("phoneHash"), hasher.hash(phone)));
                }
                String idNo = CustomerMasking.normalizeIdNo(trimmed);
                if (idNo != null && idNo.matches("^(\\d{15}|\\d{17}[0-9X])$")) {
                    textMatch = cb.or(textMatch, cb.equal(root.get("idNoHash"), hasher.hash(idNo)));
                }
                predicates.add(textMatch);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<OperationDtos.DuplicateLead> findDuplicates(String phone, String idNo, UUID excludeId, int limit) {
        Map<UUID, Lead> found = new LinkedHashMap<>();
        if (phone != null) {
            leadRepository.findByPhoneHashAndStatusIn(hasher.hash(phone), List.copyOf(DUPLICATE_ACTIVE_STATUSES))
                    .forEach(lead -> found.put(lead.getId(), lead));
        }
        if (idNo != null) {
            leadRepository.findByIdNoHashAndStatusIn(hasher.hash(idNo), List.copyOf(DUPLICATE_ACTIVE_STATUSES))
                    .forEach(lead -> found.put(lead.getId(), lead));
        }
        return found.values().stream()
                .filter(lead -> excludeId == null || !excludeId.equals(lead.getId()))
                .sorted(Comparator.comparing(Lead::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .map(lead -> new OperationDtos.DuplicateLead(
                        lead.getId(), lead.getLeadNo(), lead.getName(),
                        lead.getPhoneMasked(), lead.getStatus(), lead.getCreatedAt()))
                .toList();
    }

    private void ensureNoDuplicate(String phone, String idNo, UUID excludeId) {
        List<OperationDtos.DuplicateLead> duplicates = findDuplicates(phone, idNo, excludeId, 3);
        if (!duplicates.isEmpty()) {
            String leadNos = duplicates.stream().map(OperationDtos.DuplicateLead::leadNo)
                    .collect(Collectors.joining("、"));
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                    "手机号或证件号与既有线索重复（" + leadNos + "），请先核验合并");
        }
    }

    private Lead load(UUID leadId) {
        return leadRepository.findById(leadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "线索不存在"));
    }

    private void ensureNotTerminal(Lead lead, String message) {
        if (TERMINAL_STATUSES.contains(lead.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, message);
        }
    }

    private String requireChannel(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源渠道不能为空");
        }
        if (!channelRepository.existsByChannelCode(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "来源渠道不存在：" + code);
        }
        return code;
    }

    private UUID resolveOwner(UUID requestedOwner) {
        UUID owner = requestedOwner == null ? CurrentUser.id() : requestedOwner;
        if (!userRepository.findById(owner)
                .filter(user -> "active".equals(user.getStatus())).isPresent()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "负责人不存在或已停用");
        }
        return owner;
    }

    private String normalizePhone(String raw) {
        String normalized = CustomerMasking.normalizePhone(raw);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号需为 11 位且以 1 开头");
        }
        return normalized;
    }

    private String normalizeIdNo(String raw) {
        String normalized = CustomerMasking.normalizeIdNo(raw);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("^(\\d{15}|\\d{17}[0-9X])$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "证件号需为 15 位或 18 位身份证号");
        }
        return normalized;
    }

    private void applyPhone(Lead lead, String phone) {
        lead.setPhoneEncrypted(cipher.encrypt(phone));
        lead.setPhoneHash(hasher.hash(phone));
        lead.setPhoneMasked(CustomerMasking.maskPhone(phone));
    }

    private void applyIdNo(Lead lead, String idNo) {
        lead.setIdNoEncrypted(cipher.encrypt(idNo));
        lead.setIdNoHash(hasher.hash(idNo));
        lead.setIdNoMasked(CustomerMasking.maskIdNo(idNo));
    }

    private Map<String, String> channelNames() {
        return channelRepository.findAllByOrderByChannelCodeAsc().stream()
                .collect(Collectors.toMap(SourceChannel::getChannelCode, SourceChannel::getChannelName,
                        (left, right) -> left));
    }

    private Map<UUID, String> userNames(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (left, right) -> left));
    }

    private Map<UUID, String> userNames(UUID userId) {
        return userId == null ? Map.of() : userNames(List.of(userId));
    }

    private Set<UUID> ownerIds(Lead lead) {
        return lead.getOwnerUserId() == null ? Set.of() : Set.of(lead.getOwnerUserId());
    }

    private OperationDtos.LeadSummary toSummary(Lead lead,
                                                Map<String, String> channelNames,
                                                Map<UUID, String> ownerNames) {
        return new OperationDtos.LeadSummary(
                lead.getId(),
                lead.getLeadNo(),
                lead.getName(),
                lead.getGender(),
                lead.getBirthDate(),
                lead.getPhoneMasked(),
                lead.getIdNoMasked(),
                lead.getSourceChannelCode(),
                channelNames.getOrDefault(lead.getSourceChannelCode(), lead.getSourceChannelCode()),
                lead.getStatus(),
                lead.getOwnerUserId(),
                lead.getOwnerUserId() == null ? null : ownerNames.get(lead.getOwnerUserId()),
                lead.getOrgId(),
                lead.getConvertedCustomerId(),
                lead.getMergedIntoLeadId(),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }

    private OperationDtos.ContactRecordView toContactView(LeadContactRecord record, Map<UUID, String> names) {
        return new OperationDtos.ContactRecordView(
                record.getId(),
                record.getContactType(),
                record.getContactedAt(),
                record.getContactedBy(),
                record.getContactedBy() == null ? null : names.get(record.getContactedBy()),
                record.getContent(),
                record.getResultCode(),
                record.getNextFollowUpAt());
    }

    private OperationDtos.AssignmentView toAssignmentView(LeadAssignment assignment, Map<UUID, String> names) {
        return new OperationDtos.AssignmentView(
                assignment.getId(),
                assignment.getFromUserId(),
                assignment.getFromUserId() == null ? null : names.get(assignment.getFromUserId()),
                assignment.getToUserId(),
                names.get(assignment.getToUserId()),
                assignment.getReason(),
                assignment.getAssignedAt());
    }

    private OperationDtos.ConversionView toConversionView(LeadConversion conversion,
                                                           Map<UUID, String> names) {
        CustomerDtos.CustomerBrief customer = customerService.brief(conversion.getCustomerId());
        return toConversionView(conversion, customer.customerNo(), customer.name(), names);
    }

    private OperationDtos.ConversionView toConversionView(LeadConversion conversion,
                                                           String customerNo,
                                                           String customerName,
                                                           Map<UUID, String> names) {
        return new OperationDtos.ConversionView(
                conversion.getId(),
                conversion.getLeadId(),
                conversion.getCustomerId(),
                customerNo,
                customerName,
                conversion.getConversionMode(),
                conversion.getRemark(),
                conversion.getConvertedBy(),
                conversion.getConvertedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
