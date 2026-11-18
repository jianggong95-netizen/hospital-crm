package com.hospital.crm.callcenter.application;

import com.hospital.crm.callcenter.adapter.CallCenterAdapter;
import com.hospital.crm.callcenter.api.CallCenterDtos;
import com.hospital.crm.callcenter.domain.CallRecord;
import com.hospital.crm.callcenter.domain.CallRecordRepository;
import com.hospital.crm.callcenter.domain.CallRecording;
import com.hospital.crm.callcenter.domain.CallRecordingRepository;
import com.hospital.crm.chat.domain.ChatSession;
import com.hospital.crm.chat.domain.ChatSessionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.complaint.domain.Complaint;
import com.hospital.crm.complaint.domain.ComplaintRepository;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.system.audit.AuditService;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 呼叫中心服务：来电弹屏、通话记录录入/查询/关联与录音访问。
 *
 * 合规约束：主被叫号码仅保存 HMAC 哈希与脱敏值，明文不落库；
 * 录音访问必须经过厂商适配器换取地址并累加访问计数、写审计留痕。
 */
@Service
public class CallCenterService {

    private static final String CALL_RESOURCE = "CALL_RECORD";
    private static final String RECORDING_RESOURCE = "CALL_RECORDING";
    private static final Set<String> DIRECTIONS = Set.of("inbound", "outbound");
    private static final Set<String> CALL_STATUSES = Set.of("connected", "missed", "transferred");
    private static final Set<String> LINK_TYPES = Set.of("customer", "complaint", "session", "appointment", "none");

    private final CallRecordRepository callRecordRepository;
    private final CallRecordingRepository recordingRepository;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final ComplaintRepository complaintRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final SensitiveDataHasher hasher;
    private final CallCenterAdapter callCenterAdapter;
    private final AuditService auditService;

    public CallCenterService(CallRecordRepository callRecordRepository,
                             CallRecordingRepository recordingRepository,
                             CustomerService customerService,
                             SysUserRepository userRepository,
                             ComplaintRepository complaintRepository,
                             ChatSessionRepository chatSessionRepository,
                             SensitiveDataHasher hasher,
                             CallCenterAdapter callCenterAdapter,
                             AuditService auditService) {
        this.callRecordRepository = callRecordRepository;
        this.recordingRepository = recordingRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.hasher = hasher;
        this.callCenterAdapter = callCenterAdapter;
        this.auditService = auditService;
    }

    /**
     * 来电弹屏：按主叫号码哈希匹配启用中的客户，并给出历史通话统计；
     * 审计 detail 只记录脱敏号码与是否命中，不记录原始号码。
     */
    @Transactional(readOnly = true)
    public CallCenterDtos.PopupView incomingPopup(String phone) {
        String normalized = CustomerMasking.normalizePhone(phone);
        String phoneMasked = normalized == null ? null : CustomerMasking.maskPhone(normalized);
        String hash = normalized == null ? null : hasher.hash(normalized);

        Optional<CustomerDtos.CustomerBrief> matched = hash == null
                ? Optional.empty()
                : customerService.matchByPhone(normalized);
        long historyCount = hash == null ? 0 : callRecordRepository.countByCallerHash(hash);
        Instant lastCallAt = hash == null
                ? null
                : callRecordRepository.findFirstByCallerHashOrderByStartedAtDesc(hash)
                        .map(CallRecord::getStartedAt).orElse(null);

        CallCenterDtos.PopupCustomer customer = null;
        if (matched.isPresent()) {
            CustomerDtos.CustomerDetail detail = customerService.detail(matched.get().id());
            customer = new CallCenterDtos.PopupCustomer(
                    detail.id(), detail.customerNo(), detail.name(),
                    detail.gender(), detail.birthDate(), detail.phoneMasked(), detail.status());
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("phoneMasked", phoneMasked);
        audit.put("matched", customer != null);
        auditService.record("CALL_POPUP", CALL_RESOURCE, "popup", audit);

        return new CallCenterDtos.PopupView(customer != null, phoneMasked, customer, historyCount, lastCallAt);
    }

    @Transactional(readOnly = true)
    public PageResponse<CallCenterDtos.CallRecordView> listCalls(String keyword,
                                                                 String direction,
                                                                 String status,
                                                                 UUID customerId,
                                                                 String linkType,
                                                                 LocalDate dateFrom,
                                                                 LocalDate dateTo,
                                                                 int page,
                                                                 int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<CallRecord> result = callRecordRepository.findAll(
                buildSpecification(keyword, direction, status, customerId, linkType, dateFrom, dateTo), pageable);
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(), safePage, safePageSize);
    }

    /**
     * 录入通话记录：vendor_call_id 命中已有记录时直接返回（厂商同步幂等）；
     * 指定客户时自动关联客户档案；录音键复用同一条录音元数据。
     */
    @Transactional
    public CallCenterDtos.CallRecordDetailView createCall(CallCenterDtos.CreateCallRequest request) {
        if (request.vendorCallId() != null && !request.vendorCallId().isBlank()) {
            Optional<CallRecord> existing = callRecordRepository.findByVendorCallId(request.vendorCallId().trim());
            if (existing.isPresent()) {
                return buildDetail(existing.get());
            }
        }
        if (!DIRECTIONS.contains(request.direction())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "通话方向取值不合法");
        }
        if (!CALL_STATUSES.contains(request.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "通话状态取值不合法");
        }
        if (request.endedAt() != null && request.endedAt().isBefore(request.startedAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "通话结束时间不能早于开始时间");
        }

        CallRecord record = new CallRecord();
        record.setCallNo("CC" + callRecordRepository.nextCallNo());
        record.setVendorCallId(blankToNull(request.vendorCallId()));
        record.setSource(record.getVendorCallId() == null ? "manual" : "vendor");
        record.setDirection(request.direction());
        record.setStatus(request.status());
        applyCaller(record, request.callerNumber());
        applyCallee(record, request.calleeNumber());

        record.setStartedAt(request.startedAt());
        record.setEndedAt(request.endedAt());
        Integer duration = request.durationSeconds();
        if (duration == null && request.endedAt() != null) {
            duration = (int) Duration.between(request.startedAt(), request.endedAt()).getSeconds();
        }
        record.setDurationSeconds(duration);

        record.setQueueName(blankToNull(request.queueName()));
        record.setAgentUserId(request.agentUserId());

        String linkType = request.linkType() == null || request.linkType().isBlank() ? "none" : request.linkType();
        if (!LINK_TYPES.contains(linkType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "关联类型取值不合法");
        }
        if (request.customerId() != null) {
            CustomerDtos.CustomerBrief brief = customerService.brief(request.customerId());
            record.setCustomerId(brief.id());
            record.setLinkType("customer");
            record.setLinkId(brief.id());
            record.setLinkNo(brief.customerNo());
        } else if (!"none".equals(linkType)) {
            if (request.linkId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "指定关联类型时必须提供关联目标 ID");
            }
            record.setLinkType(linkType);
            record.setLinkId(request.linkId());
            record.setLinkNo(resolveLinkNo(linkType, request.linkId()));
        }

        if (request.recordingKey() != null && !request.recordingKey().isBlank()) {
            CallRecording recording = resolveRecording(request.recordingKey().trim(),
                    request.storageType(), duration);
            record.setRecordingId(recording.getId());
        }

        CallRecord saved = callRecordRepository.save(record);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("callNo", saved.getCallNo());
        audit.put("direction", saved.getDirection());
        audit.put("status", saved.getStatus());
        audit.put("hasRecording", saved.getRecordingId() != null);
        auditService.record("CALL_RECORD_CREATE", CALL_RESOURCE, saved.getId().toString(), audit);

        return buildDetail(saved);
    }

    @Transactional(readOnly = true)
    public CallCenterDtos.CallRecordDetailView detail(UUID callId) {
        return buildDetail(requireRecord(callId));
    }

    /**
     * 关联通话到投诉工单 / 在线客服会话 / 预约 / 客户；
     * 预约不校验存在性，避免引入 crm-appointment 模块依赖。
     */
    @Transactional
    public CallCenterDtos.CallRecordDetailView link(UUID callId, CallCenterDtos.LinkRequest request) {
        CallRecord record = requireRecord(callId);
        String targetType = request.targetType();
        if (!LINK_TYPES.contains(targetType) || "none".equals(targetType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "关联目标类型取值不合法");
        }
        String linkNo = resolveLinkNo(targetType, request.targetId());
        UUID customerId = "customer".equals(targetType) ? request.targetId() : record.getCustomerId();

        callRecordRepository.linkRecord(callId, targetType, request.targetId(), linkNo,
                customerId, Instant.now());

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("callNo", record.getCallNo());
        audit.put("targetType", targetType);
        audit.put("targetId", request.targetId().toString());
        auditService.record("CALL_RECORD_LINK", CALL_RESOURCE, callId.toString(), audit);

        return buildDetail(requireRecord(callId));
    }

    /**
     * 录音访问：换取厂商地址、累加访问计数并审计留痕；未接入呼叫中心时返回 502。
     */
    @Transactional
    public CallCenterDtos.RecordingAccessView recordingAccess(UUID callId) {
        CallRecord record = requireRecord(callId);
        if (record.getRecordingId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该通话没有录音记录");
        }
        CallRecording recording = recordingRepository.findById(record.getRecordingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "录音记录不存在"));

        String accessUrl = callCenterAdapter.resolveRecordingUrl(recording.getRecordingKey());
        recordingRepository.incrementAccess(recording.getId());

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("callNo", record.getCallNo());
        audit.put("recordingKey", recording.getRecordingKey());
        auditService.record("CALL_RECORDING_ACCESS", RECORDING_RESOURCE, recording.getId().toString(), audit);

        return new CallCenterDtos.RecordingAccessView(recording.getId(), record.getCallNo(),
                recording.getStorageType(), accessUrl, "录音访问已留痕，地址有效期以厂商策略为准");
    }

    // ---------- 私有方法 ----------

    private CallRecord requireRecord(UUID callId) {
        return callRecordRepository.findById(callId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通话记录不存在"));
    }

    private void applyCaller(CallRecord record, String callerNumber) {
        String normalized = CustomerMasking.normalizePhone(callerNumber);
        if (normalized != null) {
            record.setCallerHash(hasher.hash(normalized));
            record.setCallerMasked(CustomerMasking.maskPhone(normalized));
        }
    }

    private void applyCallee(CallRecord record, String calleeNumber) {
        String normalized = CustomerMasking.normalizePhone(calleeNumber);
        if (normalized != null) {
            record.setCalleeHash(hasher.hash(normalized));
            record.setCalleeMasked(CustomerMasking.maskPhone(normalized));
        }
    }

    /** 同一录音键复用已有记录，避免厂商重复回传造成多行。 */
    private CallRecording resolveRecording(String recordingKey, String storageType, Integer durationSeconds) {
        Optional<CallRecording> existing = recordingRepository.findByRecordingKey(recordingKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        CallRecording recording = new CallRecording();
        recording.setRecordingKey(recordingKey);
        recording.setStorageType(storageType == null || storageType.isBlank() ? "vendor" : storageType);
        recording.setDurationSeconds(durationSeconds);
        return recordingRepository.save(recording);
    }

    private String resolveLinkNo(String targetType, UUID targetId) {
        return switch (targetType) {
            case "customer" -> customerService.brief(targetId).customerNo();
            case "complaint" -> complaintRepository.findById(targetId)
                    .map(Complaint::getTicketNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "投诉工单不存在"));
            case "session" -> chatSessionRepository.findById(targetId)
                    .map(ChatSession::getSessionNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在"));
            // 预约仅记录 ID，不校验存在性，避免引入 crm-appointment 模块依赖
            case "appointment" -> null;
            default -> null;
        };
    }

    private Specification<CallRecord> buildSpecification(String keyword,
                                                         String direction,
                                                         String status,
                                                         UUID customerId,
                                                         String linkType,
                                                         LocalDate dateFrom,
                                                         LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("callNo")),
                        "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (direction != null && !direction.isBlank()) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (linkType != null && !linkType.isBlank()) {
                predicates.add(cb.equal(root.get("linkType"), linkType));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"),
                        dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("startedAt"),
                        dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CallCenterDtos.CallRecordDetailView buildDetail(CallRecord record) {
        List<CallCenterDtos.CallRecordView> views = toViews(List.of(record));
        CallCenterDtos.RecordingView recordingView = record.getRecordingId() == null
                ? null
                : recordingRepository.findById(record.getRecordingId())
                        .map(this::toRecordingView).orElse(null);
        return new CallCenterDtos.CallRecordDetailView(views.get(0), recordingView);
    }

    private List<CallCenterDtos.CallRecordView> toViews(List<CallRecord> records) {
        Set<UUID> customerIds = records.stream()
                .map(CallRecord::getCustomerId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> agentIds = records.stream()
                .map(CallRecord::getAgentUserId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerIds.isEmpty()
                ? Map.of() : customerService.briefs(customerIds);
        Map<UUID, String> agents = userNames(agentIds);

        return records.stream().map(record -> {
            CustomerDtos.CustomerBrief brief = record.getCustomerId() == null
                    ? null : customers.get(record.getCustomerId());
            return new CallCenterDtos.CallRecordView(
                record.getId(),
                record.getCallNo(),
                record.getVendorCallId(),
                record.getDirection(),
                record.getCallerMasked(),
                record.getCalleeMasked(),
                record.getCustomerId(),
                brief == null ? null : brief.name(),
                record.getLinkType(),
                record.getLinkId(),
                record.getLinkNo(),
                record.getQueueName(),
                record.getAgentUserId(),
                record.getAgentUserId() == null ? null : agents.get(record.getAgentUserId()),
                record.getStatus(),
                record.getStartedAt(),
                record.getEndedAt(),
                record.getDurationSeconds(),
                record.getRecordingId() != null,
                record.getRecordingId(),
                record.getSource(),
                record.getCreatedAt());
        }).toList();
    }

    private CallCenterDtos.RecordingView toRecordingView(CallRecording recording) {
        return new CallCenterDtos.RecordingView(
                recording.getId(),
                recording.getRecordingKey(),
                recording.getStorageType(),
                recording.getDurationSeconds(),
                recording.getFileSize(),
                recording.getAccessCount(),
                recording.getCreatedAt());
    }

    private Map<UUID, String> userNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
