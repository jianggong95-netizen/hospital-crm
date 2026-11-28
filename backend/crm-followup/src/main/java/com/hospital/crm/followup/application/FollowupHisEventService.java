package com.hospital.crm.followup.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerIdentityService;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.followup.api.FollowupDtos;
import com.hospital.crm.followup.domain.FollowupCategory;
import com.hospital.crm.followup.domain.FollowupCategoryRepository;
import com.hospital.crm.followup.domain.FollowupHisEvent;
import com.hospital.crm.followup.domain.FollowupHisEventRepository;
import com.hospital.crm.followup.domain.FollowupPlan;
import com.hospital.crm.followup.domain.FollowupPlanRepository;
import com.hospital.crm.followup.support.FollowupVisitTypes;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HIS 事件接入服务（随访与 HIS 的接口预留层）：
 * HIS/集成平台按统一事件契约推送门诊就诊、出院、手术、体检报告事件，
 * 事件以 eventKey 幂等落库，再按启用中的随访计划自动生成任务。
 * 真实 HIS 对接时只需让集成平台调用本接口（或在此替换为客户解析适配器），
 * 业务侧计划/模板配置无需改动。
 */
@Service
public class FollowupHisEventService {

    /** 与 crm-his 模块 HisPatientService 绑定时使用的身份类型一致。 */
    private static final String HIS_IDENTITY_TYPE = "his_patient_id";
    private static final Set<String> EVENT_TYPES =
            Set.of("HIS_OUTPATIENT_VISIT", "HIS_DISCHARGE", "HIS_SURGERY", "HIS_EXAM_REPORT");

    /**
     * HIS 事件目录静态定义（依据 V13/V21 迁移种子数据整理）：
     * 事件类型 → 回访板块 → 必备 HIS 字段与触发说明。
     * defaultCategories / recommendedPlans 在运行时按当前启用配置动态回填。
     * 营销回访板块（MARKETING）无 HIS 事件触发，由运营活动手工/批量建单，不在本目录内。
     */
    private static final List<CatalogDef> CATALOG_DEFS = List.of(
            new CatalogDef("HIS_OUTPATIENT_VISIT", "门诊就诊完成事件", "OUTPATIENT",
                    List.of("eventKey（门诊流水号，全局唯一，幂等键）",
                            "患者标识（customerId 或 externalPatientId=HIS 病案号）",
                            "occurredAt（就诊日期）", "就诊科室", "接诊医师", "诊断摘要",
                            "处方/医嘱（可选，用于用药指导随访）"),
                    "门诊就诊完成后按启用计划生成随访：默认次日门诊满意度问卷（FP100010，h5）、"
                            + "第 14 天复诊提醒（FP100011，phone）；板块下另有用药指导、慢病等自定义分类可扩展"),
            new CatalogDef("HIS_DISCHARGE", "出院事件", "INPATIENT",
                    List.of("eventKey（住院号+出院日期，全局唯一，幂等键）",
                            "患者标识（customerId 或 externalPatientId=住院号/病案号）",
                            "occurredAt（出院日期）", "出院科室", "出院诊断",
                            "主治医生", "出院医嘱/用药清单"),
                    "出院后按启用计划生成随访：默认第 7 天恢复与用药随访（FP100012，phone）、"
                            + "第 30 天复诊随访（FP100013，phone）；高风险专科可扩展专病随访计划"),
            new CatalogDef("HIS_SURGERY", "手术完成事件", "SURGERY",
                    List.of("eventKey（手术申请单号/手术编号，全局唯一，幂等键）",
                            "患者标识（customerId 或 externalPatientId=住院号）",
                            "occurredAt（手术日期）", "手术名称", "手术科室", "术者", "麻醉方式"),
                    "手术后按启用计划生成随访：默认第 3 天伤口与疼痛随访（FP100014，phone）、"
                            + "第 14 天复查提醒（FP100015，phone）；术前告知类分类走预约/人工触发"),
            new CatalogDef("HIS_EXAM_REPORT", "体检报告出具事件", "EXAM",
                    List.of("eventKey（体检编号+报告日期，全局唯一，幂等键）",
                            "患者标识（customerId 或 externalPatientId=体检号）",
                            "occurredAt（报告出具日期）", "异常指标清单（重大异常需标记级别）",
                            "复查/转诊建议"),
                    "体检报告出具后按启用计划生成随访：默认次日异常结果通知（FP100016，phone）、"
                            + "第 2 天满意度问卷（FP100017，h5）；板块下另有复查提醒、健康管理计划等自定义分类"));

    private final FollowupHisEventRepository eventRepository;
    private final FollowupTaskService taskService;
    private final CustomerService customerService;
    private final CustomerIdentityService identityService;
    private final FollowupCategoryRepository categoryRepository;
    private final FollowupPlanRepository planRepository;
    private final AuditService auditService;

    public FollowupHisEventService(FollowupHisEventRepository eventRepository,
                                   FollowupTaskService taskService,
                                   CustomerService customerService,
                                   CustomerIdentityService identityService,
                                   FollowupCategoryRepository categoryRepository,
                                   FollowupPlanRepository planRepository,
                                   AuditService auditService) {
        this.eventRepository = eventRepository;
        this.taskService = taskService;
        this.customerService = customerService;
        this.identityService = identityService;
        this.categoryRepository = categoryRepository;
        this.planRepository = planRepository;
        this.auditService = auditService;
    }

    /**
     * HIS 事件 → 随访映射目录：事件类型、映射板块、板块下启用中的默认分类、
     * 启用中的推荐计划与必备 HIS 字段清单，供前端配置页与对接文档使用。
     */
    @Transactional(readOnly = true)
    public List<FollowupDtos.HisEventCatalogItem> catalog() {
        Map<String, List<String>> categoriesByVisitType = categoryRepository
                .findByStatusOrderBySortNoAsc("active").stream()
                .collect(Collectors.groupingBy(FollowupCategory::getVisitType, LinkedHashMap::new,
                        Collectors.mapping(FollowupCategory::getCategoryCode, Collectors.toList())));
        Map<String, List<String>> plansByTriggerType = planRepository.findByStatus("active").stream()
                .collect(Collectors.groupingBy(FollowupPlan::getTriggerType, LinkedHashMap::new,
                        Collectors.mapping(FollowupPlan::getPlanNo, Collectors.toList())));
        return CATALOG_DEFS.stream()
                .map(def -> new FollowupDtos.HisEventCatalogItem(def.eventType(), def.eventTypeLabel(),
                        def.visitType(), FollowupVisitTypes.label(def.visitType()),
                        categoriesByVisitType.getOrDefault(def.visitType(), List.of()),
                        plansByTriggerType.getOrDefault(def.eventType(), List.of()),
                        def.requiredHisFields(), def.triggerDescription()))
                .toList();
    }

    /** 目录静态定义条目。 */
    private record CatalogDef(String eventType, String eventTypeLabel, String visitType,
                              List<String> requiredHisFields, String triggerDescription) {
    }

    @Transactional
    public FollowupDtos.HisEventView receive(FollowupDtos.HisEventRequest request) {
        if (!EVENT_TYPES.contains(request.eventType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 HIS 事件类型");
        }
        if ((request.customerId() == null || request.customerId().toString().isBlank())
                && (request.externalPatientId() == null || request.externalPatientId().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "customerId 与 externalPatientId 至少提供一个");
        }

        // 幂等：同一事件键重复推送直接返回首次处理结果
        Optional<FollowupHisEvent> duplicate = eventRepository.findByEventKey(request.eventKey());
        if (duplicate.isPresent()) {
            return toView(duplicate.get());
        }

        UUID customerId = request.customerId();
        if (customerId == null) {
            customerId = identityService
                    .findCustomerId(HIS_IDENTITY_TYPE, request.externalPatientId())
                    .orElse(null);
        }

        FollowupHisEvent event = new FollowupHisEvent();
        event.setEventKey(request.eventKey());
        event.setEventType(request.eventType());
        event.setExternalPatientId(request.externalPatientId());
        event.setSourceSystem(request.sourceSystem() == null || request.sourceSystem().isBlank()
                ? "HIS" : request.sourceSystem());
        event.setOccurredAt(request.occurredAt() == null ? Instant.now() : request.occurredAt());
        event.setPayload(request.payload());

        if (customerId == null) {
            event.setCustomerId(null);
            event.setStatus("NO_MATCH");
            event.setErrorMessage("未找到匹配的 CRM 客户：请携带 customerId 或先在 HIS 集成页完成患者绑定");
            eventRepository.save(event);
            auditService.record("FOLLOWUP_HIS_EVENT", "CRM_FOLLOWUP_HIS_EVENT",
                    event.getEventKey(), Map.of("status", "NO_MATCH", "eventType", event.getEventType()));
            return toView(event);
        }

        event.setCustomerId(customerId);
        FollowupDtos.TriggerResult result = taskService.trigger(new FollowupDtos.TriggerRequest(
                request.eventType(), request.eventKey(), customerId, event.getOccurredAt()));
        event.setStatus("PROCESSED");
        event.setCreatedTasks(result.created());
        event.setSkippedTasks(result.skipped());
        eventRepository.save(event);
        auditService.record("FOLLOWUP_HIS_EVENT", "CRM_FOLLOWUP_HIS_EVENT",
                event.getEventKey(), Map.of("status", "PROCESSED", "eventType", event.getEventType(),
                        "created", result.created(), "skipped", result.skipped()));
        return toView(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<FollowupDtos.HisEventView> list(String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        PageRequest pageable = PageRequest.of(safePage - 1, safePageSize);
        Page<FollowupHisEvent> result = status == null || status.isBlank()
                ? eventRepository.findAllByOrderByCreatedAtDesc(pageable)
                : eventRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(), safePage, safePageSize);
    }

    private List<FollowupDtos.HisEventView> toViews(List<FollowupHisEvent> events) {
        Set<UUID> customerIds = events.stream()
                .map(FollowupHisEvent::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, CustomerDtos.CustomerBrief> briefs = customerIds.isEmpty()
                ? new LinkedHashMap<>() : customerService.briefs(customerIds);
        return events.stream().map(event -> {
            CustomerDtos.CustomerBrief brief = event.getCustomerId() == null
                    ? null : briefs.get(event.getCustomerId());
            return new FollowupDtos.HisEventView(event.getId(), event.getEventKey(), event.getEventType(),
                    event.getCustomerId(), brief == null ? null : brief.name(),
                    event.getExternalPatientId(), event.getSourceSystem(), event.getOccurredAt(),
                    event.getStatus(), event.getCreatedTasks(), event.getSkippedTasks(),
                    event.getErrorMessage(), event.getCreatedAt());
        }).toList();
    }

    private FollowupDtos.HisEventView toView(FollowupHisEvent event) {
        String customerName = null;
        if (event.getCustomerId() != null) {
            try {
                customerName = customerService.brief(event.getCustomerId()).name();
            } catch (RuntimeException ignored) {
                // 客户可能已被删除，不影响事件展示
            }
        }
        return new FollowupDtos.HisEventView(event.getId(), event.getEventKey(), event.getEventType(),
                event.getCustomerId(), customerName, event.getExternalPatientId(), event.getSourceSystem(),
                event.getOccurredAt(), event.getStatus(), event.getCreatedTasks(), event.getSkippedTasks(),
                event.getErrorMessage(), event.getCreatedAt());
    }
}
