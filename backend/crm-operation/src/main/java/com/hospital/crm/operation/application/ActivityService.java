package com.hospital.crm.operation.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.operation.api.OperationDtos;
import com.hospital.crm.operation.domain.ActivityParticipant;
import com.hospital.crm.operation.domain.ActivityParticipantRepository;
import com.hospital.crm.operation.domain.LeadRepository;
import com.hospital.crm.operation.domain.OperationActivity;
import com.hospital.crm.operation.domain.OperationActivityRepository;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 活动与参与人服务：活动发布后方可报名，报名按手机号去重，参与状态可签到或标记爽约。
 */
@Service
public class ActivityService {

    private static final String RESOURCE = "ACTIVITY";
    private static final Map<String, Set<String>> ACTIVITY_TRANSITIONS = Map.of(
            "DRAFT", Set.of("PUBLISHED", "CANCELLED"),
            "PUBLISHED", Set.of("CLOSED", "CANCELLED")
    );
    private static final Map<String, Set<String>> PARTICIPANT_TRANSITIONS = Map.of(
            "REGISTERED", Set.of("ATTENDED", "NO_SHOW", "CANCELLED"),
            "NO_SHOW", Set.of("ATTENDED", "CANCELLED")
    );

    private final OperationActivityRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final LeadRepository leadRepository;
    private final CustomerService customerService;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public ActivityService(OperationActivityRepository activityRepository,
                           ActivityParticipantRepository participantRepository,
                           LeadRepository leadRepository,
                           CustomerService customerService,
                           SensitiveDataCipher cipher,
                           SensitiveDataHasher hasher,
                           AuditService auditService) {
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.leadRepository = leadRepository;
        this.customerService = customerService;
        this.cipher = cipher;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationDtos.ActivityView> list(String keyword,
                                                         String status,
                                                         int page,
                                                         int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "startAt"));
        Page<OperationActivity> result = activityRepository.findAll(
                buildSpecification(keyword, status), pageable);
        Map<UUID, Long> counts = participantCounts(result.getContent().stream()
                .map(OperationActivity::getId).toList());
        List<OperationDtos.ActivityView> items = result.getContent().stream()
                .map(activity -> toView(activity, counts.getOrDefault(activity.getId(), 0L)))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public OperationDtos.ActivityView detail(UUID activityId) {
        OperationActivity activity = load(activityId);
        long count = participantRepository.countByActivityIdAndStatusNot(activityId, "CANCELLED");
        return toView(activity, count);
    }

    @Transactional
    public OperationDtos.ActivityView create(OperationDtos.ActivityCreateRequest request) {
        validateTimes(request.startAt(), request.endAt());
        if (request.capacity() != null && request.capacity() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "活动名额不能为负数");
        }
        OperationActivity activity = new OperationActivity();
        activity.setActivityNo("A" + activityRepository.nextActivityNo());
        activity.setActivityName(request.activityName().trim());
        activity.setActivityType(request.activityType() == null ? "other" : request.activityType());
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
        activity.setLocation(blankToNull(request.location()));
        activity.setCapacity(request.capacity());
        activity.setStatus("DRAFT");
        activity.setDescription(blankToNull(request.description()));
        activity.setCreatedBy(CurrentUser.id());
        activity = activityRepository.save(activity);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("activityNo", activity.getActivityNo());
        audit.put("activityName", activity.getActivityName());
        audit.put("startAt", activity.getStartAt().toString());
        auditService.record("ACTIVITY_CREATE", RESOURCE, activity.getId().toString(), audit);

        return toView(activity, 0L);
    }

    @Transactional
    public OperationDtos.ActivityView changeStatus(UUID activityId, OperationDtos.ActivityStatusRequest request) {
        OperationActivity activity = load(activityId);
        String target = request.status().trim().toUpperCase();
        Set<String> allowed = ACTIVITY_TRANSITIONS.getOrDefault(activity.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "活动状态不允许从 " + activity.getStatus() + " 变更为 " + target);
        }
        String previous = activity.getStatus();
        activity.setStatus(target);
        activity = activityRepository.save(activity);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("activityNo", activity.getActivityNo());
        audit.put("fromStatus", previous);
        audit.put("toStatus", target);
        auditService.record("ACTIVITY_STATUS_CHANGE", RESOURCE, activityId.toString(), audit);

        long count = participantRepository.countByActivityIdAndStatusNot(activityId, "CANCELLED");
        return toView(activity, count);
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationDtos.ParticipantView> participants(UUID activityId, int page, int pageSize) {
        load(activityId);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<ActivityParticipant> result = participantRepository.findByActivityIdOrderByRegisteredAtDesc(
                activityId, PageRequest.of(safePage - 1, safePageSize));
        return PageResponse.of(result.getContent().stream().map(this::toParticipantView).toList(),
                result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public OperationDtos.ParticipantView register(UUID activityId,
                                                  OperationDtos.ParticipantCreateRequest request) {
        OperationActivity activity = load(activityId);
        if (!"PUBLISHED".equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅已发布活动允许报名");
        }
        long activeCount = participantRepository.countByActivityIdAndStatusNot(activityId, "CANCELLED");
        if (activity.getCapacity() != null && activeCount >= activity.getCapacity()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "活动名额已满");
        }
        if (request.leadId() != null) {
            leadRepository.findById(request.leadId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "关联线索不存在"));
        }
        if (request.customerId() != null) {
            customerService.brief(request.customerId());
        }
        String phone = normalizePhone(request.phone());
        String phoneHash = hasher.hash(phone);
        if (phoneHash != null && participantRepository
                .findFirstByActivityIdAndPhoneHash(activityId, phoneHash).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该手机号已报名此活动");
        }

        ActivityParticipant participant = new ActivityParticipant();
        participant.setActivityId(activityId);
        participant.setLeadId(request.leadId());
        participant.setCustomerId(request.customerId());
        participant.setParticipantName(request.participantName().trim());
        participant.setPhoneEncrypted(cipher.encrypt(phone));
        participant.setPhoneHash(phoneHash);
        participant.setPhoneMasked(CustomerMasking.maskPhone(phone));
        participant.setStatus("REGISTERED");
        participant.setRemark(blankToNull(request.remark()));
        participant = participantRepository.save(participant);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("activityNo", activity.getActivityNo());
        audit.put("participantName", participant.getParticipantName());
        auditService.record("ACTIVITY_PARTICIPANT_REGISTER", RESOURCE, activityId.toString(), audit);

        return toParticipantView(participant);
    }

    @Transactional
    public OperationDtos.ParticipantView changeParticipantStatus(UUID activityId,
                                                                 UUID participantId,
                                                                 OperationDtos.ParticipantStatusRequest request) {
        load(activityId);
        ActivityParticipant participant = participantRepository.findById(participantId)
                .filter(item -> activityId.equals(item.getActivityId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "活动参与人不存在"));
        String target = request.status().trim().toUpperCase();
        Set<String> allowed = PARTICIPANT_TRANSITIONS.getOrDefault(participant.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "参与状态不允许从 " + participant.getStatus() + " 变更为 " + target);
        }
        participant.setStatus(target);
        if ("ATTENDED".equals(target) && participant.getCheckedInAt() == null) {
            participant.setCheckedInAt(Instant.now());
        }
        if (request.remark() != null) {
            participant.setRemark(blankToNull(request.remark()));
        }
        participant = participantRepository.save(participant);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("participantId", participantId.toString());
        audit.put("toStatus", target);
        auditService.record("ACTIVITY_PARTICIPANT_STATUS_CHANGE", RESOURCE, activityId.toString(), audit);

        return toParticipantView(participant);
    }

    private Specification<OperationActivity> buildSpecification(String keyword, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("activityNo")), like),
                        cb.like(cb.lower(root.get("activityName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private OperationActivity load(UUID activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "活动不存在"));
    }

    private void validateTimes(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "活动开始与结束时间不能为空");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "活动结束时间必须晚于开始时间");
        }
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

    private Map<UUID, Long> participantCounts(Collection<UUID> activityIds) {
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Object[] row : participantRepository.countActiveByActivityIds(activityIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private OperationDtos.ActivityView toView(OperationActivity activity, long participantCount) {
        return new OperationDtos.ActivityView(
                activity.getId(),
                activity.getActivityNo(),
                activity.getActivityName(),
                activity.getActivityType(),
                activity.getStartAt(),
                activity.getEndAt(),
                activity.getLocation(),
                activity.getCapacity(),
                activity.getStatus(),
                activity.getDescription(),
                participantCount,
                activity.getCreatedAt(),
                activity.getUpdatedAt());
    }

    private OperationDtos.ParticipantView toParticipantView(ActivityParticipant participant) {
        return new OperationDtos.ParticipantView(
                participant.getId(),
                participant.getActivityId(),
                participant.getLeadId(),
                participant.getCustomerId(),
                participant.getParticipantName(),
                participant.getPhoneMasked(),
                participant.getStatus(),
                participant.getRegisteredAt(),
                participant.getCheckedInAt(),
                participant.getRemark());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
