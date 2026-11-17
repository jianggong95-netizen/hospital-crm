package com.hospital.crm.followup.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.followup.api.FollowupDtos;
import com.hospital.crm.followup.domain.FollowupCategory;
import com.hospital.crm.followup.domain.FollowupCategoryRepository;
import com.hospital.crm.followup.domain.FollowupPlan;
import com.hospital.crm.followup.domain.FollowupPlanRepository;
import com.hospital.crm.followup.domain.FollowupRecord;
import com.hospital.crm.followup.domain.FollowupRecordRepository;
import com.hospital.crm.followup.domain.FollowupTask;
import com.hospital.crm.followup.domain.FollowupTaskRepository;
import com.hospital.crm.followup.domain.FollowupTemplate;
import com.hospital.crm.followup.domain.FollowupTemplateRepository;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 随访任务服务：手工建单、事件触发建单、分配、开始、提交结果、人工升级与处置。
 * 同一计划同一事件键只生成一个任务；高风险答案自动进入人工队列（ESCALATED）。
 */
@Service
public class FollowupTaskService {

    private static final String RESOURCE = "CRM_FOLLOWUP_TASK";
    private static final Set<String> CHANNELS =
            Set.of("phone", "sms", "mini_program", "h5", "ai", "manual");
    private static final Set<String> TRIGGER_TYPES =
            Set.of("APPOINTMENT_COMPLETED", "SERVICE_COMPLETED", "ORDER_PAID", "MANUAL");

    private final FollowupTaskRepository taskRepository;
    private final FollowupRecordRepository recordRepository;
    private final FollowupPlanRepository planRepository;
    private final FollowupCategoryRepository categoryRepository;
    private final FollowupTemplateRepository templateRepository;
    private final FollowupConfigService configService;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public FollowupTaskService(FollowupTaskRepository taskRepository,
                               FollowupRecordRepository recordRepository,
                               FollowupPlanRepository planRepository,
                               FollowupCategoryRepository categoryRepository,
                               FollowupTemplateRepository templateRepository,
                               FollowupConfigService configService,
                               CustomerService customerService,
                               SysUserRepository userRepository,
                               AuditService auditService) {
        this.taskRepository = taskRepository;
        this.recordRepository = recordRepository;
        this.planRepository = planRepository;
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.configService = configService;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<FollowupDtos.TaskView> list(String keyword,
                                                    String status,
                                                    String categoryCode,
                                                    String riskLevel,
                                                    UUID assigneeId,
                                                    UUID customerId,
                                                    Boolean escalationRequired,
                                                    LocalDate dateFrom,
                                                    LocalDate dateTo,
                                                    int page,
                                                    int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.ASC, "plannedAt"));
        Page<FollowupTask> result = taskRepository.findAll(
                buildSpecification(keyword, status, categoryCode, riskLevel, assigneeId, customerId,
                        escalationRequired, dateFrom, dateTo),
                pageable);
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public FollowupDtos.TaskDetail detail(UUID taskId) {
        FollowupTask task = requireTask(taskId);
        FollowupDtos.TaskView view = toViews(List.of(task)).get(0);
        List<Map<String, Object>> questions = task.getTemplateCode() == null ? List.of()
                : templateRepository.findByTemplateCode(task.getTemplateCode())
                        .map(FollowupTemplate::getQuestions)
                        .orElse(List.of());
        List<FollowupDtos.RecordView> records = recordRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(record -> new FollowupDtos.RecordView(record.getId(), record.getRiskLevel(),
                        record.getAnswers(), record.getResultCode(), record.getRemark(),
                        record.getOperatorId(), record.getCreatedAt()))
                .toList();
        return new FollowupDtos.TaskDetail(view, questions, records);
    }

    // ------------------------------------------------------------------
    // 建单
    // ------------------------------------------------------------------
    @Transactional
    public FollowupDtos.TaskView createManual(FollowupDtos.TaskCreateRequest request) {
        FollowupCategory category = configService.requireActiveCategory(request.categoryCode());
        validateChannel(request.channel());
        FollowupTemplate template = null;
        if (request.templateCode() != null && !request.templateCode().isBlank()) {
            template = configService.requireActiveTemplate(request.templateCode());
            if (!template.getCategoryCode().equals(category.getCategoryCode())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "模板不属于所选随访分类");
            }
        }
        customerService.brief(request.customerId());
        validateAssignee(request.assigneeId());

        FollowupTask task = new FollowupTask();
        task.setTaskNo(nextTaskNo());
        task.setCustomerId(request.customerId());
        task.setCategoryCode(category.getCategoryCode());
        task.setTemplateCode(template == null ? null : template.getTemplateCode());
        task.setChannel(request.channel());
        task.setPlannedAt(request.plannedAt());
        task.setAssigneeId(request.assigneeId());
        if (request.assigneeId() != null) {
            task.setAssignedAt(Instant.now());
        }
        taskRepository.save(task);
        auditService.record("FOLLOWUP_TASK_CREATE", RESOURCE, task.getTaskNo(),
                Map.of("customerId", task.getCustomerId().toString(),
                        "categoryCode", task.getCategoryCode()));
        return toViews(List.of(task)).get(0);
    }

    @Transactional
    public FollowupDtos.TriggerResult trigger(FollowupDtos.TriggerRequest request) {
        if (!TRIGGER_TYPES.contains(request.triggerType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的随访触发类型");
        }
        customerService.brief(request.customerId());
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        List<FollowupPlan> plans = planRepository
                .findByStatusAndTriggerTypeOrderByPlanNoAsc("active", request.triggerType());
        int created = 0;
        int skipped = 0;
        List<FollowupTask> tasks = new ArrayList<>();
        for (FollowupPlan plan : plans) {
            if (taskRepository.existsByPlanIdAndTriggerEventKey(plan.getId(), request.eventKey())) {
                skipped++;
                continue;
            }
            FollowupTask task = new FollowupTask();
            task.setTaskNo(nextTaskNo());
            task.setCustomerId(request.customerId());
            task.setCategoryCode(plan.getCategoryCode());
            task.setTemplateCode(plan.getTemplateCode());
            task.setChannel(plan.getChannel());
            task.setPlannedAt(occurredAt.plus(plan.getOffsetDays(), ChronoUnit.DAYS));
            task.setPlanId(plan.getId());
            task.setTriggerType(plan.getTriggerType());
            task.setTriggerEventKey(request.eventKey());
            if (plan.getDefaultAssigneeId() != null) {
                task.setAssigneeId(plan.getDefaultAssigneeId());
                task.setAssignedAt(Instant.now());
            }
            taskRepository.save(task);
            tasks.add(task);
            created++;
        }
        auditService.record("FOLLOWUP_TRIGGER", RESOURCE, request.eventKey(),
                Map.of("triggerType", request.triggerType(), "created", created, "skipped", skipped));
        return new FollowupDtos.TriggerResult(created, skipped, toViews(tasks));
    }

    // ------------------------------------------------------------------
    // 任务流转
    // ------------------------------------------------------------------
    @Transactional
    public FollowupDtos.TaskView assign(UUID taskId, FollowupDtos.AssignRequest request) {
        FollowupTask task = requireTask(taskId);
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待执行的随访任务可分配");
        }
        validateAssignee(request.assigneeId());
        task.setAssigneeId(request.assigneeId());
        task.setAssignedAt(Instant.now());
        taskRepository.save(task);
        auditService.record("FOLLOWUP_TASK_ASSIGN", RESOURCE, task.getTaskNo(),
                Map.of("assigneeId", request.assigneeId().toString()));
        return toViews(List.of(task)).get(0);
    }

    @Transactional
    public FollowupDtos.TaskView start(UUID taskId) {
        FollowupTask task = requireTask(taskId);
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待执行的随访任务可开始");
        }
        task.setStatus("IN_PROGRESS");
        task.setStartedAt(Instant.now());
        taskRepository.save(task);
        auditService.record("FOLLOWUP_TASK_START", RESOURCE, task.getTaskNo(), Map.of());
        return toViews(List.of(task)).get(0);
    }

    @Transactional
    public FollowupDtos.TaskDetail submitRecord(UUID taskId, FollowupDtos.RecordCreateRequest request) {
        FollowupTask task = requireTask(taskId);
        if (recordRepository.existsByTaskId(taskId)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该随访任务已提交结果");
        }
        if (!Set.of("PENDING", "IN_PROGRESS").contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "当前状态的随访任务不能提交结果");
        }
        Map<String, String> answers = request.answers() == null ? Map.of() : request.answers();
        String riskLevel = evaluateRisk(task.getTemplateCode(), answers);
        Instant now = Instant.now();

        FollowupRecord record = new FollowupRecord();
        record.setTaskId(task.getId());
        record.setTemplateCode(task.getTemplateCode());
        record.setAnswers(new LinkedHashMap<>(answers));
        record.setRiskLevel(riskLevel);
        record.setResultCode(request.resultCode());
        record.setRemark(request.remark());
        record.setOperatorId(currentUserId());
        recordRepository.save(record);

        task.setRiskLevel(riskLevel);
        task.setResultCode(request.resultCode());
        task.setResultPayload(new LinkedHashMap<>(answers));
        if (FollowupRiskEvaluator.HIGH.equals(riskLevel)) {
            task.setStatus("ESCALATED");
            task.setEscalationRequired(true);
            task.setEscalatedAt(now);
            task.setEscalationReason("随访回答触发高风险规则，转入人工队列");
        } else {
            task.setStatus("COMPLETED");
            task.setCompletedAt(now);
        }
        taskRepository.save(task);
        auditService.record("FOLLOWUP_RECORD", RESOURCE, task.getTaskNo(),
                Map.of("riskLevel", riskLevel, "status", task.getStatus()));
        return detail(taskId);
    }

    @Transactional
    public FollowupDtos.TaskView escalate(UUID taskId, FollowupDtos.ReasonRequest request) {
        FollowupTask task = requireTask(taskId);
        if (!Set.of("PENDING", "IN_PROGRESS").contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待执行或执行中的随访任务可升级");
        }
        task.setStatus("ESCALATED");
        task.setEscalationRequired(true);
        task.setEscalatedAt(Instant.now());
        task.setEscalationReason(request.reason());
        taskRepository.save(task);
        auditService.record("FOLLOWUP_ESCALATE", RESOURCE, task.getTaskNo(),
                Map.of("reason", request.reason()));
        return toViews(List.of(task)).get(0);
    }

    @Transactional
    public FollowupDtos.TaskView resolve(UUID taskId, FollowupDtos.ResolveRequest request) {
        FollowupTask task = requireTask(taskId);
        if (!"ESCALATED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅人工队列中的随访任务可处置");
        }
        task.setStatus("COMPLETED");
        task.setCompletedAt(Instant.now());
        Map<String, Object> payload = new LinkedHashMap<>(task.getResultPayload());
        if (request.remark() != null && !request.remark().isBlank()) {
            payload.put("resolution", request.remark());
            task.setResultPayload(payload);
        }
        taskRepository.save(task);
        auditService.record("FOLLOWUP_RESOLVE", RESOURCE, task.getTaskNo(),
                Map.of("remark", request.remark() == null ? "" : request.remark()));
        return toViews(List.of(task)).get(0);
    }

    @Transactional
    public FollowupDtos.TaskView cancel(UUID taskId, FollowupDtos.ReasonRequest request) {
        FollowupTask task = requireTask(taskId);
        if (!Set.of("PENDING", "IN_PROGRESS", "ESCALATED").contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "终态随访任务不能取消");
        }
        task.setStatus("CANCELLED");
        task.setEscalationReason(request.reason());
        taskRepository.save(task);
        auditService.record("FOLLOWUP_CANCEL", RESOURCE, task.getTaskNo(),
                Map.of("reason", request.reason()));
        return toViews(List.of(task)).get(0);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private Specification<FollowupTask> buildSpecification(String keyword,
                                                           String status,
                                                           String categoryCode,
                                                           String riskLevel,
                                                           UUID assigneeId,
                                                           UUID customerId,
                                                           Boolean escalationRequired,
                                                           LocalDate dateFrom,
                                                           LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("taskNo")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (categoryCode != null && !categoryCode.isBlank()) {
                predicates.add(cb.equal(root.get("categoryCode"), categoryCode));
            }
            if (riskLevel != null && !riskLevel.isBlank()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (escalationRequired != null) {
                predicates.add(cb.equal(root.get("escalationRequired"), escalationRequired));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("plannedAt"),
                        dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("plannedAt"),
                        dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<FollowupDtos.TaskView> toViews(List<FollowupTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                tasks.stream().map(FollowupTask::getCustomerId).collect(Collectors.toSet()));
        Map<String, String> categories = new LinkedHashMap<>();
        categoryRepository.findAll().forEach(category ->
                categories.put(category.getCategoryCode(), category.getCategoryName()));
        Map<UUID, String> assignees = new LinkedHashMap<>();
        userRepository.findAllById(tasks.stream().map(FollowupTask::getAssigneeId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .forEach(user -> assignees.put(user.getId(), user.getDisplayName()));
        Map<UUID, String> plans = new LinkedHashMap<>();
        planRepository.findAllById(tasks.stream().map(FollowupTask::getPlanId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .forEach(plan -> plans.put(plan.getId(), plan.getPlanNo()));
        return tasks.stream().map(task -> {
            CustomerDtos.CustomerBrief customer = customers.get(task.getCustomerId());
            return new FollowupDtos.TaskView(
                    task.getId(),
                    task.getTaskNo(),
                    task.getCustomerId(),
                    customer == null ? null : customer.name(),
                    customer == null ? null : customer.phoneMasked(),
                    task.getCategoryCode(),
                    categories.get(task.getCategoryCode()),
                    task.getTemplateCode(),
                    task.getChannel(),
                    task.getAssigneeId(),
                    task.getAssigneeId() == null ? null : assignees.get(task.getAssigneeId()),
                    task.getPlannedAt(),
                    task.getStatus(),
                    task.getRiskLevel(),
                    task.isEscalationRequired(),
                    task.getResultCode(),
                    task.getCompletedAt(),
                    task.getCreatedAt(),
                    task.getPlanId() == null ? null : plans.get(task.getPlanId()));
        }).toList();
    }

    private String evaluateRisk(String templateCode, Map<String, String> answers) {
        if (templateCode == null) {
            return FollowupRiskEvaluator.LOW;
        }
        return templateRepository.findByTemplateCode(templateCode)
                .map(template -> FollowupRiskEvaluator.evaluate(template.getRiskRules(), answers))
                .orElse(FollowupRiskEvaluator.LOW);
    }

    private FollowupTask requireTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "随访任务不存在"));
    }

    private void validateChannel(String channel) {
        if (channel == null || !CHANNELS.contains(channel)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的随访渠道");
        }
    }

    private void validateAssignee(UUID assigneeId) {
        if (assigneeId != null && !userRepository.existsById(assigneeId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "随访人不存在");
        }
    }

    private String nextTaskNo() {
        return String.format("FT%d", taskRepository.nextTaskNo());
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.principal().userId();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
