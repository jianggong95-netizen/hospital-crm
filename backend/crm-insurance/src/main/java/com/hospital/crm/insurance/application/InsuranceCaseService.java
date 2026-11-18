package com.hospital.crm.insurance.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.insurance.api.InsuranceDtos;
import com.hospital.crm.insurance.domain.InsuranceCase;
import com.hospital.crm.insurance.domain.InsuranceCaseRepository;
import com.hospital.crm.insurance.domain.InsuranceCompany;
import com.hospital.crm.insurance.domain.InsuranceCompanyRepository;
import com.hospital.crm.insurance.domain.InsuranceEmail;
import com.hospital.crm.insurance.domain.InsuranceEmailAttachment;
import com.hospital.crm.insurance.domain.InsuranceEmailAttachmentRepository;
import com.hospital.crm.insurance.domain.InsuranceEmailRepository;
import com.hospital.crm.insurance.domain.InsuranceEvent;
import com.hospital.crm.insurance.domain.InsuranceEventRepository;
import com.hospital.crm.insurance.domain.InsuranceMaterial;
import com.hospital.crm.insurance.domain.InsuranceMaterialRepository;
import com.hospital.crm.insurance.domain.InsurancePolicy;
import com.hospital.crm.insurance.domain.InsurancePolicyRepository;
import com.hospital.crm.insurance.support.InsuranceStateMachine;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 商保案件服务：建案、分派、状态机流转、材料与往来邮件登记。
 *
 * <p>金额口径：estimated_payout 仅在审批通过/部分通过时写入，actual_received 仅在对账时写入，
 * 两者为独立字段，任何流转都不得互相覆盖。每次状态变化都会写入一条 crm_insurance_event 并记录审计。</p>
 */
@Service
public class InsuranceCaseService {

    private static final String CASE_RESOURCE = "INSURANCE_CASE";
    private static final String MATERIAL_RESOURCE = "INSURANCE_MATERIAL";
    private static final String EMAIL_RESOURCE = "INSURANCE_EMAIL";
    private static final Set<String> CASE_TYPES = Set.of("outpatient", "inpatient", "physical_exam", "other");
    private static final Set<String> MATERIAL_TYPES =
            Set.of("diagnosis", "invoice", "detail", "prescription", "report", "other");
    private static final Set<String> MATERIAL_STATUSES = Set.of("valid", "invalid");
    private static final Set<String> EMAIL_DIRECTIONS = Set.of("inbound", "outbound");
    private static final Pattern FILE_HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private final InsuranceCaseRepository caseRepository;
    private final InsurancePolicyRepository policyRepository;
    private final InsuranceCompanyRepository companyRepository;
    private final InsuranceMaterialRepository materialRepository;
    private final InsuranceEmailRepository emailRepository;
    private final InsuranceEmailAttachmentRepository attachmentRepository;
    private final InsuranceEventRepository eventRepository;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public InsuranceCaseService(InsuranceCaseRepository caseRepository,
                                InsurancePolicyRepository policyRepository,
                                InsuranceCompanyRepository companyRepository,
                                InsuranceMaterialRepository materialRepository,
                                InsuranceEmailRepository emailRepository,
                                InsuranceEmailAttachmentRepository attachmentRepository,
                                InsuranceEventRepository eventRepository,
                                CustomerService customerService,
                                SysUserRepository userRepository,
                                AuditService auditService) {
        this.caseRepository = caseRepository;
        this.policyRepository = policyRepository;
        this.companyRepository = companyRepository;
        this.materialRepository = materialRepository;
        this.emailRepository = emailRepository;
        this.attachmentRepository = attachmentRepository;
        this.eventRepository = eventRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<InsuranceDtos.CaseView> list(String keyword, UUID companyId, UUID policyId,
                                                     String state, UUID customerId, UUID handlerUserId,
                                                     LocalDate dateFrom, LocalDate dateTo,
                                                     int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InsuranceCase> result = caseRepository.findAll(
                buildSpecification(keyword, companyId, policyId, state, customerId, handlerUserId,
                        dateFrom, dateTo),
                pageable);
        return PageResponse.of(toCaseViews(result.getContent()), result.getTotalElements(),
                safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public InsuranceDtos.CaseDetailView detail(UUID caseId) {
        return buildDetail(requireCase(caseId));
    }

    @Transactional(readOnly = true)
    public List<InsuranceDtos.EventView> events(UUID caseId) {
        requireCase(caseId);
        return toEventViews(eventRepository.findByCaseIdOrderByCreatedAtAsc(caseId));
    }

    // ------------------------------------------------------------------
    // 建案、分派与流转
    // ------------------------------------------------------------------
    @Transactional
    public InsuranceDtos.CaseDetailView create(InsuranceDtos.CreateCaseRequest request) {
        InsurancePolicy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "保单不存在"));
        if (!"active".equals(policy.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "保单状态不是生效中，不能建案");
        }
        if (policy.getValidTo() != null && policy.getValidTo().isBefore(LocalDate.now(ZoneId.systemDefault()))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "保单已过期，不能建案");
        }
        String caseType = request.caseType() == null || request.caseType().isBlank()
                ? "other" : request.caseType();
        if (!CASE_TYPES.contains(caseType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "案件类型取值不合法");
        }
        BigDecimal receivable = request.hospitalReceivable() == null
                ? BigDecimal.ZERO : request.hospitalReceivable();
        BigDecimal patientPay = request.patientPay() == null
                ? BigDecimal.ZERO : request.patientPay();
        requireNonNegative(receivable, "医院应收不能为负数");
        requireNonNegative(patientPay, "患者自付不能为负数");

        InsuranceCase insuranceCase = new InsuranceCase();
        insuranceCase.setCaseNo("IC" + caseRepository.nextCaseNo());
        insuranceCase.setCompanyId(policy.getCompanyId());
        insuranceCase.setPolicyId(policy.getId());
        insuranceCase.setCustomerId(policy.getCustomerId());
        insuranceCase.setCaseType(caseType);
        insuranceCase.setServiceDesc(blankToNull(request.serviceDesc()));
        insuranceCase.setState(InsuranceStateMachine.REGISTERED);
        insuranceCase.setHospitalReceivable(receivable);
        insuranceCase.setPatientPay(patientPay);
        insuranceCase.setEstimatedPayout(BigDecimal.ZERO);
        insuranceCase.setActualReceived(BigDecimal.ZERO);
        insuranceCase.setRemark(blankToNull(request.remark()));
        insuranceCase.setCreatedBy(currentUserId());
        InsuranceCase saved = caseRepository.save(insuranceCase);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("policyNo", policy.getPolicyNo());
        writeEvent(saved, "REGISTER", null, InsuranceStateMachine.REGISTERED, detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("caseNo", saved.getCaseNo());
        audit.put("policyNo", policy.getPolicyNo());
        audit.put("companyId", saved.getCompanyId().toString());
        auditService.record("INSURANCE_CASE_CREATE", CASE_RESOURCE, saved.getId().toString(), audit);
        return buildDetail(saved);
    }

    @Transactional
    public InsuranceDtos.CaseDetailView assign(UUID caseId, InsuranceDtos.AssignRequest request) {
        InsuranceCase insuranceCase = requireCase(caseId);
        if (!userRepository.existsById(request.handlerUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "处理人不存在");
        }
        insuranceCase.setHandlerUserId(request.handlerUserId());
        InsuranceCase saved = caseRepository.save(insuranceCase);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("handlerUserId", request.handlerUserId().toString());
        writeEvent(saved, "ASSIGN", saved.getState(), saved.getState(), detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("caseNo", saved.getCaseNo());
        audit.put("handlerUserId", request.handlerUserId().toString());
        auditService.record("INSURANCE_CASE_ASSIGN", CASE_RESOURCE, caseId.toString(), audit);
        return buildDetail(saved);
    }

    @Transactional
    public InsuranceDtos.CaseDetailView transition(UUID caseId, InsuranceDtos.TransitionRequest request) {
        InsuranceCase insuranceCase = requireCase(caseId);
        String fromState = insuranceCase.getState();
        String targetState = request.targetState();
        InsuranceStateMachine.requireTransition(fromState, targetState);

        Instant now = Instant.now();
        Map<String, Object> detail = new LinkedHashMap<>();
        switch (targetState) {
            case InsuranceStateMachine.QUOTATION -> {
                if (request.quotationAmount() == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "进入询价状态必须填写询价金额");
                }
                requireNonNegative(request.quotationAmount(), "询价金额不能为负数");
                insuranceCase.setQuotationAmount(request.quotationAmount());
                insuranceCase.setQuotationAt(now);
                detail.put("quotationAmount", request.quotationAmount());
            }
            case InsuranceStateMachine.PRE_AUTHORIZATION -> {
                String preauthNo = blankToNull(request.preauthNo());
                if (preauthNo == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "进入预授权状态必须填写预授权号");
                }
                if (request.preauthAmount() != null) {
                    requireNonNegative(request.preauthAmount(), "预授权金额不能为负数");
                    insuranceCase.setPreauthAmount(request.preauthAmount());
                    detail.put("preauthAmount", request.preauthAmount());
                }
                insuranceCase.setPreauthNo(preauthNo);
                insuranceCase.setPreauthAt(now);
                detail.put("preauthNo", preauthNo);
            }
            case InsuranceStateMachine.CLAIM_SUBMITTED -> {
                String claimNo = blankToNull(request.claimNo());
                if (claimNo == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "提交理赔必须填写理赔号");
                }
                if (request.hospitalReceivable() != null) {
                    requireNonNegative(request.hospitalReceivable(), "医院应收不能为负数");
                    insuranceCase.setHospitalReceivable(request.hospitalReceivable());
                    detail.put("hospitalReceivable", request.hospitalReceivable());
                }
                if (request.patientPay() != null) {
                    requireNonNegative(request.patientPay(), "患者自付不能为负数");
                    insuranceCase.setPatientPay(request.patientPay());
                    detail.put("patientPay", request.patientPay());
                }
                insuranceCase.setClaimNo(claimNo);
                insuranceCase.setClaimSubmittedAt(now);
                detail.put("claimNo", claimNo);
            }
            case InsuranceStateMachine.APPROVED, InsuranceStateMachine.PARTIALLY_APPROVED -> {
                BigDecimal approvedAmount = request.approvedAmount();
                if (approvedAmount == null || approvedAmount.signum() <= 0) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "理赔核定金额必须大于 0");
                }
                BigDecimal receivable = insuranceCase.getHospitalReceivable();
                if (receivable != null && receivable.signum() > 0
                        && approvedAmount.compareTo(receivable) > 0) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "核定赔付金额不能大于医院应收金额");
                }
                insuranceCase.setClaimDecision(targetState);
                insuranceCase.setApprovedAmount(approvedAmount);
                // 预计赔付仅在此处写入，禁止用实际到账覆写
                insuranceCase.setEstimatedPayout(approvedAmount);
                insuranceCase.setDecidedAt(now);
                if (request.note() != null) {
                    insuranceCase.setDecisionReason(request.note());
                }
                detail.put("approvedAmount", approvedAmount);
            }
            case InsuranceStateMachine.REJECTED -> {
                String note = blankToNull(request.note());
                if (note == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "驳回必须填写原因");
                }
                insuranceCase.setClaimDecision(InsuranceStateMachine.REJECTED);
                insuranceCase.setDecisionReason(note);
                insuranceCase.setDecidedAt(now);
                insuranceCase.setEstimatedPayout(BigDecimal.ZERO);
                detail.put("decisionReason", note);
            }
            case InsuranceStateMachine.RECONCILED -> {
                BigDecimal actualReceived = request.actualReceived();
                if (actualReceived == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "对账必须填写实际到账金额");
                }
                requireNonNegative(actualReceived, "实际到账金额不能为负数");
                BigDecimal estimated = insuranceCase.getEstimatedPayout() == null
                        ? BigDecimal.ZERO : insuranceCase.getEstimatedPayout();
                BigDecimal diff = actualReceived.subtract(estimated);
                // 实际到账独立写入，不覆盖预计赔付
                insuranceCase.setActualReceived(actualReceived);
                insuranceCase.setReconciledAt(now);
                insuranceCase.setReconciliationDiff(diff);
                detail.put("actualReceived", actualReceived);
                detail.put("estimatedPayout", estimated);
                detail.put("reconciliationDiff", diff);
            }
            default -> {
                // 其余迁移无附加字段
            }
        }
        if (request.note() != null && !detail.containsKey("decisionReason")) {
            detail.put("note", request.note());
        }
        insuranceCase.setState(targetState);
        InsuranceCase saved = caseRepository.save(insuranceCase);

        writeEvent(saved, "STATE_CHANGE", fromState, targetState, detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("caseNo", saved.getCaseNo());
        audit.put("from", fromState);
        audit.put("to", targetState);
        if (saved.getEstimatedPayout() != null) {
            audit.put("estimatedPayout", saved.getEstimatedPayout());
        }
        if (saved.getActualReceived() != null) {
            audit.put("actualReceived", saved.getActualReceived());
        }
        auditService.record("INSURANCE_CASE_TRANSITION", CASE_RESOURCE, caseId.toString(), audit);
        return buildDetail(saved);
    }

    // ------------------------------------------------------------------
    // 材料
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<InsuranceDtos.MaterialView> materials(UUID caseId) {
        requireCase(caseId);
        List<InsuranceMaterial> materials = materialRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
        Set<UUID> userIds = materials.stream().map(InsuranceMaterial::getUploadedBy)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> names = userNames(userIds);
        return materials.stream().map(material -> toMaterialView(material, names)).toList();
    }

    @Transactional
    public InsuranceDtos.MaterialView addMaterial(UUID caseId, InsuranceDtos.MaterialCreateRequest request) {
        InsuranceCase insuranceCase = requireCase(caseId);
        if (InsuranceStateMachine.RECONCILED.equals(insuranceCase.getState())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已对账案件不能新增材料");
        }
        if (request.materialType() == null || !MATERIAL_TYPES.contains(request.materialType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "材料类型取值不合法");
        }
        validateFileHash(request.fileHash(), "文件哈希必须为 64 位十六进制");

        InsuranceMaterial material = new InsuranceMaterial();
        material.setCaseId(caseId);
        material.setMaterialType(request.materialType());
        material.setFileName(request.fileName());
        material.setFileKey(request.fileKey());
        material.setFileHash(request.fileHash());
        material.setFileSize(request.fileSize());
        material.setSource("manual");
        material.setStatus("pending");
        material.setNote(blankToNull(request.note()));
        material.setUploadedBy(currentUserId());
        InsuranceMaterial saved = materialRepository.save(material);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("materialId", saved.getId() == null ? "" : saved.getId().toString());
        detail.put("materialType", saved.getMaterialType());
        detail.put("fileHash", saved.getFileHash());
        writeEvent(insuranceCase, "MATERIAL_ADD", insuranceCase.getState(), insuranceCase.getState(), detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("caseNo", insuranceCase.getCaseNo());
        audit.put("materialType", saved.getMaterialType());
        auditService.record("INSURANCE_MATERIAL_ADD", MATERIAL_RESOURCE, saved.getId().toString(), audit);
        return toMaterialView(saved, userNames(saved.getUploadedBy() == null
                ? Set.of() : Set.of(saved.getUploadedBy())));
    }

    @Transactional
    public InsuranceDtos.MaterialView updateMaterialStatus(UUID materialId, String status) {
        if (!MATERIAL_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "材料状态取值不合法");
        }
        InsuranceMaterial material = requireMaterial(materialId);
        materialRepository.updateStatus(materialId, status);
        InsuranceMaterial refreshed = requireMaterial(materialId);
        InsuranceCase insuranceCase = requireCase(material.getCaseId());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("materialId", materialId.toString());
        detail.put("status", status);
        writeEvent(insuranceCase, "MATERIAL_STATUS", insuranceCase.getState(), insuranceCase.getState(), detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("caseNo", insuranceCase.getCaseNo());
        audit.put("status", status);
        auditService.record("INSURANCE_MATERIAL_STATUS", MATERIAL_RESOURCE, materialId.toString(), audit);
        return toMaterialView(refreshed, userNames(refreshed.getUploadedBy() == null
                ? Set.of() : Set.of(refreshed.getUploadedBy())));
    }

    // ------------------------------------------------------------------
    // 邮件
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<InsuranceDtos.EmailView> emails(UUID caseId) {
        requireCase(caseId);
        List<InsuranceEmail> emails = emailRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
        Map<UUID, List<InsuranceEmailAttachment>> attachments = attachmentsByEmail(emails);
        Set<UUID> operatorIds = emails.stream().map(InsuranceEmail::getOperatorUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> names = userNames(operatorIds);
        return emails.stream()
                .map(email -> toEmailView(email, attachments.getOrDefault(email.getId(), List.of()), names))
                .toList();
    }

    @Transactional
    public InsuranceDtos.EmailView addEmail(UUID caseId, InsuranceDtos.EmailCreateRequest request) {
        InsuranceCase insuranceCase = requireCase(caseId);
        String direction = request.direction();
        if (direction == null || !EMAIL_DIRECTIONS.contains(direction)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邮件方向取值不合法");
        }
        List<InsuranceDtos.AttachmentCreateRequest> attachments = request.attachments() == null
                ? List.of() : request.attachments();
        for (InsuranceDtos.AttachmentCreateRequest attachment : attachments) {
            validateFileHash(attachment.fileHash(), "附件哈希必须为 64 位十六进制");
        }
        String messageId = blankToNull(request.messageId());

        // 入站邮件按 Message-ID 幂等：已入库直接返回既有记录
        if ("inbound".equals(direction) && messageId != null) {
            Optional<InsuranceEmail> existing = emailRepository.findByMessageId(messageId);
            if (existing.isPresent()) {
                InsuranceEmail email = existing.get();
                List<InsuranceEmailAttachment> existingAttachments = attachmentRepository
                        .findByEmailIdInOrderByCreatedAtAsc(List.of(email.getId()));
                return toEmailView(email, nullToEmpty(existingAttachments),
                        userNames(email.getOperatorUserId() == null
                                ? Set.of() : Set.of(email.getOperatorUserId())));
            }
        }

        InsuranceEmail email = new InsuranceEmail();
        email.setCaseId(caseId);
        email.setDirection(direction);
        email.setMessageId(messageId);
        email.setSubject(request.subject());
        email.setFromAddress(blankToNull(request.fromAddress()));
        email.setToAddress(blankToNull(request.toAddress()));
        email.setBodyExcerpt(blankToNull(request.bodyExcerpt()));
        email.setSentAt(request.sentAt());
        email.setReceivedAt(request.receivedAt());
        email.setAttachmentCount(attachments.size());
        email.setOperatorUserId(currentUserId());
        email.setStatus("inbound".equals(direction) ? "received" : "sent");
        InsuranceEmail saved = emailRepository.save(email);

        List<InsuranceEmailAttachment> savedAttachments = new ArrayList<>();
        for (InsuranceDtos.AttachmentCreateRequest item : attachments) {
            InsuranceEmailAttachment attachment = new InsuranceEmailAttachment();
            attachment.setEmailId(saved.getId());
            attachment.setFileName(item.fileName());
            attachment.setFileHash(item.fileHash());
            attachment.setFileSize(item.fileSize());
            savedAttachments.add(attachmentRepository.save(attachment));
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("messageId", messageId);
        detail.put("attachmentCount", savedAttachments.size());
        writeEvent(insuranceCase, "inbound".equals(direction) ? "EMAIL_INBOUND" : "EMAIL_OUTBOUND",
                insuranceCase.getState(), insuranceCase.getState(), detail);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("caseNo", insuranceCase.getCaseNo());
        audit.put("messageId", messageId);
        audit.put("attachmentCount", savedAttachments.size());
        auditService.record("INSURANCE_EMAIL_RECORD", EMAIL_RESOURCE, saved.getId().toString(), audit);
        return toEmailView(saved, savedAttachments, userNames(saved.getOperatorUserId() == null
                ? Set.of() : Set.of(saved.getOperatorUserId())));
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private InsuranceCase requireCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商保案件不存在"));
    }

    private InsuranceMaterial requireMaterial(UUID materialId) {
        return materialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "案件材料不存在"));
    }

    private void writeEvent(InsuranceCase insuranceCase, String eventType, String fromState,
                            String toState, Map<String, Object> detail) {
        InsuranceEvent event = new InsuranceEvent();
        event.setCaseId(insuranceCase.getId());
        event.setEventType(eventType);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setOperatorUserId(currentUserId());
        event.setDetail(detail == null ? new LinkedHashMap<>() : detail);
        eventRepository.save(event);
    }

    private InsuranceDtos.CaseDetailView buildDetail(InsuranceCase insuranceCase) {
        InsuranceDtos.CaseView caseView = toCaseViews(List.of(insuranceCase)).get(0);
        return new InsuranceDtos.CaseDetailView(
                caseView,
                materialsOf(insuranceCase.getId()),
                emailsOf(insuranceCase.getId()),
                toEventViews(eventRepository.findByCaseIdOrderByCreatedAtAsc(insuranceCase.getId())));
    }

    private List<InsuranceDtos.MaterialView> materialsOf(UUID caseId) {
        List<InsuranceMaterial> materials = materialRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        Set<UUID> userIds = materials.stream().map(InsuranceMaterial::getUploadedBy)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> names = userNames(userIds);
        return materials.stream().map(material -> toMaterialView(material, names)).toList();
    }

    private List<InsuranceDtos.EmailView> emailsOf(UUID caseId) {
        List<InsuranceEmail> emails = emailRepository.findByCaseIdOrderByCreatedAtAsc(caseId);
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<InsuranceEmailAttachment>> attachments = attachmentsByEmail(emails);
        Set<UUID> operatorIds = emails.stream().map(InsuranceEmail::getOperatorUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> names = userNames(operatorIds);
        return emails.stream()
                .map(email -> toEmailView(email, attachments.getOrDefault(email.getId(), List.of()), names))
                .toList();
    }

    private List<InsuranceDtos.EventView> toEventViews(List<InsuranceEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Set<UUID> operatorIds = events.stream().map(InsuranceEvent::getOperatorUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> names = userNames(operatorIds);
        return events.stream().map(event -> new InsuranceDtos.EventView(
                event.getId(),
                event.getEventType(),
                event.getFromState(),
                event.getToState(),
                event.getOperatorUserId(),
                event.getOperatorUserId() == null ? null : names.get(event.getOperatorUserId()),
                event.getDetail() == null ? Map.of() : event.getDetail(),
                event.getCreatedAt())).toList();
    }

    private Map<UUID, List<InsuranceEmailAttachment>> attachmentsByEmail(List<InsuranceEmail> emails) {
        Set<UUID> emailIds = emails.stream().map(InsuranceEmail::getId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (emailIds.isEmpty()) {
            return Map.of();
        }
        List<InsuranceEmailAttachment> attachments =
                attachmentRepository.findByEmailIdInOrderByCreatedAtAsc(emailIds);
        if (attachments == null) {
            return Map.of();
        }
        return attachments.stream().collect(Collectors.groupingBy(InsuranceEmailAttachment::getEmailId));
    }

    private InsuranceDtos.MaterialView toMaterialView(InsuranceMaterial material, Map<UUID, String> names) {
        return new InsuranceDtos.MaterialView(
                material.getId(),
                material.getCaseId(),
                material.getMaterialType(),
                material.getFileName(),
                material.getFileKey(),
                material.getFileHash(),
                material.getFileSize(),
                material.getSource(),
                material.getStatus(),
                material.getNote(),
                material.getUploadedBy(),
                material.getUploadedBy() == null ? null : names.get(material.getUploadedBy()),
                material.getCreatedAt());
    }

    private InsuranceDtos.EmailView toEmailView(InsuranceEmail email,
                                                List<InsuranceEmailAttachment> attachments,
                                                Map<UUID, String> names) {
        List<InsuranceDtos.AttachmentView> attachmentViews = attachments == null ? List.of()
                : attachments.stream().map(attachment -> new InsuranceDtos.AttachmentView(
                        attachment.getId(),
                        attachment.getFileName(),
                        attachment.getFileHash(),
                        attachment.getFileSize(),
                        attachment.getMaterialId(),
                        attachment.getCreatedAt())).toList();
        return new InsuranceDtos.EmailView(
                email.getId(),
                email.getCaseId(),
                email.getDirection(),
                email.getMessageId(),
                email.getSubject(),
                email.getFromAddress(),
                email.getToAddress(),
                email.getBodyExcerpt(),
                email.getSentAt(),
                email.getReceivedAt(),
                email.getAttachmentCount(),
                email.getOperatorUserId(),
                email.getOperatorUserId() == null ? null : names.get(email.getOperatorUserId()),
                email.getStatus(),
                email.getCreatedAt(),
                attachmentViews);
    }

    private List<InsuranceDtos.CaseView> toCaseViews(List<InsuranceCase> cases) {
        if (cases.isEmpty()) {
            return List.of();
        }
        Set<UUID> companyIds = cases.stream().map(InsuranceCase::getCompanyId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Set<UUID> policyIds = cases.stream().map(InsuranceCase::getPolicyId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Set<UUID> customerIds = cases.stream().map(InsuranceCase::getCustomerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Set<UUID> handlerIds = cases.stream().map(InsuranceCase::getHandlerUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));

        Map<UUID, String> companyNames = companyIds.isEmpty() ? Map.of()
                : companyRepository.findAllById(companyIds).stream()
                        .collect(Collectors.toMap(InsuranceCompany::getId, InsuranceCompany::getCompanyName));
        Map<UUID, String> policyNos = policyIds.isEmpty() ? Map.of()
                : policyRepository.findAllById(policyIds).stream()
                        .collect(Collectors.toMap(InsurancePolicy::getId, InsurancePolicy::getPolicyNo));
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerIds.isEmpty() ? Map.of()
                : customerService.briefs(customerIds);
        Map<UUID, String> handlerNames = userNames(handlerIds);

        return cases.stream().map(insuranceCase -> {
            CustomerDtos.CustomerBrief customer = customers.get(insuranceCase.getCustomerId());
            return new InsuranceDtos.CaseView(
                    insuranceCase.getId(),
                    insuranceCase.getCaseNo(),
                    insuranceCase.getCompanyId(),
                    companyNames.get(insuranceCase.getCompanyId()),
                    insuranceCase.getPolicyId(),
                    policyNos.get(insuranceCase.getPolicyId()),
                    insuranceCase.getCustomerId(),
                    customer == null ? null : customer.name(),
                    customer == null ? null : customer.customerNo(),
                    insuranceCase.getCaseType(),
                    insuranceCase.getServiceDesc(),
                    insuranceCase.getState(),
                    insuranceCase.getHospitalReceivable(),
                    insuranceCase.getPatientPay(),
                    insuranceCase.getEstimatedPayout(),
                    insuranceCase.getActualReceived(),
                    insuranceCase.getQuotationAmount(),
                    insuranceCase.getQuotationAt(),
                    insuranceCase.getPreauthNo(),
                    insuranceCase.getPreauthAmount(),
                    insuranceCase.getPreauthAt(),
                    insuranceCase.getClaimNo(),
                    insuranceCase.getClaimSubmittedAt(),
                    insuranceCase.getClaimDecision(),
                    insuranceCase.getApprovedAmount(),
                    insuranceCase.getDecisionReason(),
                    insuranceCase.getDecidedAt(),
                    insuranceCase.getHandlerUserId(),
                    insuranceCase.getHandlerUserId() == null
                            ? null : handlerNames.get(insuranceCase.getHandlerUserId()),
                    insuranceCase.getReconciledAt(),
                    insuranceCase.getReconciliationDiff(),
                    insuranceCase.getRemark(),
                    insuranceCase.getCreatedBy(),
                    insuranceCase.getCreatedAt(),
                    insuranceCase.getUpdatedAt());
        }).toList();
    }

    private Specification<InsuranceCase> buildSpecification(String keyword, UUID companyId, UUID policyId,
                                                            String state, UUID customerId,
                                                            UUID handlerUserId, LocalDate dateFrom,
                                                            LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("caseNo")), like),
                        cb.like(cb.lower(root.get("serviceDesc")), like),
                        cb.like(cb.lower(root.get("claimNo")), like)));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (policyId != null) {
                predicates.add(cb.equal(root.get("policyId"), policyId));
            }
            if (state != null && !state.isBlank()) {
                predicates.add(cb.equal(root.get("state"), state));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (handlerUserId != null) {
                predicates.add(cb.equal(root.get("handlerUserId"), handlerUserId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<UUID, String> userNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = userRepository.findAllById(userIds);
        if (users == null) {
            return Map.of();
        }
        return users.stream().collect(Collectors.toMap(SysUser::getId,
                user -> user.getDisplayName() == null || user.getDisplayName().isBlank()
                        ? user.getUsername() : user.getDisplayName()));
    }

    private void validateFileHash(String fileHash, String message) {
        if (fileHash == null || !FILE_HASH_PATTERN.matcher(fileHash).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requireNonNegative(BigDecimal amount, String message) {
        if (amount.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
