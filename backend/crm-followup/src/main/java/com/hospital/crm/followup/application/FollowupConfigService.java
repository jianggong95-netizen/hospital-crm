package com.hospital.crm.followup.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.followup.api.FollowupDtos;
import com.hospital.crm.followup.domain.FollowupCategory;
import com.hospital.crm.followup.domain.FollowupCategoryRepository;
import com.hospital.crm.followup.domain.FollowupPlan;
import com.hospital.crm.followup.domain.FollowupPlanRepository;
import com.hospital.crm.followup.domain.FollowupTemplate;
import com.hospital.crm.followup.domain.FollowupTemplateRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 随访配置服务：分类、模板与计划的维护。模板更新递增版本；
 * 计划必须引用启用的分类与同分类模板，触发类型与偏移天数受约束。
 */
@Service
public class FollowupConfigService {

    private static final Set<String> CHANNELS =
            Set.of("phone", "sms", "mini_program", "h5", "ai", "manual");
    private static final Set<String> TRIGGER_TYPES =
            Set.of("APPOINTMENT_COMPLETED", "SERVICE_COMPLETED", "ORDER_PAID", "MANUAL",
                    "HIS_OUTPATIENT_VISIT", "HIS_DISCHARGE", "HIS_SURGERY", "HIS_EXAM_REPORT");
    private static final Set<String> CONFIG_STATUSES = Set.of("active", "disabled");
    private static final Set<String> VISIT_TYPES =
            Set.of("OUTPATIENT", "INPATIENT", "SURGERY", "EXAM", "MARKETING");

    private final FollowupCategoryRepository categoryRepository;
    private final FollowupTemplateRepository templateRepository;
    private final FollowupPlanRepository planRepository;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public FollowupConfigService(FollowupCategoryRepository categoryRepository,
                                 FollowupTemplateRepository templateRepository,
                                 FollowupPlanRepository planRepository,
                                 SysUserRepository userRepository,
                                 AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<FollowupDtos.CategoryView> listCategories(String status, String visitType) {
        List<FollowupCategory> categories = status == null || status.isBlank()
                ? categoryRepository.findAllByOrderBySortNoAsc()
                : categoryRepository.findByStatusOrderBySortNoAsc(status);
        if (visitType != null && !visitType.isBlank()) {
            categories = categories.stream()
                    .filter(category -> visitType.equals(category.getVisitType()))
                    .toList();
        }
        return categories.stream().map(this::toCategoryView).toList();
    }

    @Transactional
    public FollowupDtos.CategoryView createCategory(FollowupDtos.CategoryCreateRequest request) {
        categoryRepository.findByCategoryCode(request.categoryCode()).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "随访分类编码已存在");
        });
        validateVisitType(request.visitType());
        FollowupCategory category = new FollowupCategory();
        category.setCategoryCode(request.categoryCode());
        category.setCategoryName(request.categoryName());
        category.setVisitType(request.visitType());
        category.setDescription(request.description());
        category.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        categoryRepository.save(category);
        auditService.record("FOLLOWUP_CATEGORY_CREATE", "CRM_FOLLOWUP_CATEGORY",
                category.getCategoryCode(), Map.of("name", category.getCategoryName(),
                        "visitType", category.getVisitType()));
        return toCategoryView(category);
    }

    @Transactional
    public FollowupDtos.CategoryView updateCategory(String categoryCode, FollowupDtos.CategoryUpdateRequest request) {
        FollowupCategory category = requireCategory(categoryCode);
        category.setCategoryName(request.categoryName());
        if (request.visitType() != null && !request.visitType().isBlank()) {
            validateVisitType(request.visitType());
            category.setVisitType(request.visitType());
        }
        category.setDescription(request.description());
        if (request.sortNo() != null) {
            category.setSortNo(request.sortNo());
        }
        auditService.record("FOLLOWUP_CATEGORY_UPDATE", "CRM_FOLLOWUP_CATEGORY",
                categoryCode, Map.of("name", category.getCategoryName()));
        return toCategoryView(categoryRepository.save(category));
    }

    // ------------------------------------------------------------------
    // 模板
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<FollowupDtos.TemplateView> listTemplates(String categoryCode, String status) {
        List<FollowupTemplate> templates;
        if (categoryCode != null && !categoryCode.isBlank()) {
            templates = templateRepository.findByCategoryCodeOrderByTemplateCodeAsc(categoryCode);
        } else if (status != null && !status.isBlank()) {
            templates = templateRepository.findByStatusOrderByTemplateCodeAsc(status);
        } else {
            templates = templateRepository.findAllByOrderByTemplateCodeAsc();
        }
        return templates.stream().map(this::toTemplateView).toList();
    }

    @Transactional
    public FollowupDtos.TemplateView createTemplate(FollowupDtos.TemplateCreateRequest request) {
        templateRepository.findByTemplateCode(request.templateCode()).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "随访模板编码已存在");
        });
        FollowupCategory category = requireCategory(request.categoryCode());
        if (!"active".equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "随访分类已停用，不能新建模板");
        }
        validateChannel(request.channel());
        FollowupTemplate template = new FollowupTemplate();
        template.setTemplateCode(request.templateCode());
        template.setCategoryCode(request.categoryCode());
        template.setTemplateName(request.templateName());
        template.setChannel(request.channel());
        template.setQuestions(request.questions() == null ? new ArrayList<>() : request.questions());
        template.setRiskRules(request.riskRules() == null ? new LinkedHashMap<>() : request.riskRules());
        template.setDescription(request.description());
        templateRepository.save(template);
        auditService.record("FOLLOWUP_TEMPLATE_CREATE", "CRM_FOLLOWUP_TEMPLATE",
                template.getTemplateCode(), Map.of("name", template.getTemplateName()));
        return toTemplateView(template);
    }

    @Transactional
    public FollowupDtos.TemplateView updateTemplate(String templateCode, FollowupDtos.TemplateUpdateRequest request) {
        FollowupTemplate template = requireTemplate(templateCode);
        validateChannel(request.channel());
        template.setTemplateName(request.templateName());
        template.setChannel(request.channel());
        template.setQuestions(request.questions() == null ? new ArrayList<>() : request.questions());
        template.setRiskRules(request.riskRules() == null ? new LinkedHashMap<>() : request.riskRules());
        template.setDescription(request.description());
        template.setVersion(template.getVersion() + 1);
        auditService.record("FOLLOWUP_TEMPLATE_UPDATE", "CRM_FOLLOWUP_TEMPLATE",
                templateCode, Map.of("version", template.getVersion()));
        return toTemplateView(templateRepository.save(template));
    }

    @Transactional
    public FollowupDtos.TemplateView changeTemplateStatus(String templateCode, FollowupDtos.StatusRequest request) {
        FollowupTemplate template = requireTemplate(templateCode);
        String status = normalizeStatus(request.status());
        template.setStatus(status);
        auditService.record("FOLLOWUP_TEMPLATE_STATUS", "CRM_FOLLOWUP_TEMPLATE",
                templateCode, Map.of("status", status));
        return toTemplateView(templateRepository.save(template));
    }

    // ------------------------------------------------------------------
    // 计划
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<FollowupDtos.PlanView> listPlans(String status) {
        List<FollowupPlan> plans = status == null || status.isBlank()
                ? planRepository.findAllByOrderByPlanNoAsc()
                : planRepository.findByStatus(status);
        Map<String, String> categoryNames = new LinkedHashMap<>();
        categoryRepository.findAll().forEach(category ->
                categoryNames.put(category.getCategoryCode(), category.getCategoryName()));
        return plans.stream().map(plan -> toPlanView(plan, categoryNames)).toList();
    }

    @Transactional
    public FollowupDtos.PlanView createPlan(FollowupDtos.PlanCreateRequest request) {
        FollowupCategory category = requireCategory(request.categoryCode());
        FollowupTemplate template = requireTemplate(request.templateCode());
        if (!template.getCategoryCode().equals(request.categoryCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "模板不属于所选随访分类");
        }
        if (!"active".equals(category.getStatus()) || !"active".equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "分类或模板已停用，不能新建计划");
        }
        validateTriggerType(request.triggerType());
        validateChannel(request.channel());
        validateOffset(request.offsetDays());
        FollowupPlan plan = new FollowupPlan();
        plan.setPlanNo(String.format("FP%d", planRepository.nextPlanNo()));
        plan.setPlanName(request.planName());
        plan.setCategoryCode(request.categoryCode());
        plan.setTemplateCode(request.templateCode());
        plan.setTriggerType(request.triggerType());
        plan.setOffsetDays(request.offsetDays());
        plan.setChannel(request.channel());
        plan.setDefaultAssigneeId(request.defaultAssigneeId());
        plan.setDescription(request.description());
        planRepository.save(plan);
        auditService.record("FOLLOWUP_PLAN_CREATE", "CRM_FOLLOWUP_PLAN",
                plan.getPlanNo(), Map.of("triggerType", plan.getTriggerType()));
        return toPlanView(plan, Map.of(category.getCategoryCode(), category.getCategoryName()));
    }

    @Transactional
    public FollowupDtos.PlanView updatePlan(String planNo, FollowupDtos.PlanUpdateRequest request) {
        FollowupPlan plan = requirePlan(planNo);
        validateTriggerType(request.triggerType());
        validateChannel(request.channel());
        validateOffset(request.offsetDays());
        plan.setPlanName(request.planName());
        plan.setTriggerType(request.triggerType());
        plan.setOffsetDays(request.offsetDays());
        plan.setChannel(request.channel());
        plan.setDefaultAssigneeId(request.defaultAssigneeId());
        plan.setDescription(request.description());
        auditService.record("FOLLOWUP_PLAN_UPDATE", "CRM_FOLLOWUP_PLAN",
                planNo, Map.of("triggerType", plan.getTriggerType()));
        return toPlanView(planRepository.save(plan), categoryNames());
    }

    @Transactional
    public FollowupDtos.PlanView changePlanStatus(String planNo, FollowupDtos.StatusRequest request) {
        FollowupPlan plan = requirePlan(planNo);
        String status = normalizeStatus(request.status());
        plan.setStatus(status);
        auditService.record("FOLLOWUP_PLAN_STATUS", "CRM_FOLLOWUP_PLAN",
                planNo, Map.of("status", status));
        return toPlanView(planRepository.save(plan), categoryNames());
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private Map<String, String> categoryNames() {
        Map<String, String> names = new LinkedHashMap<>();
        categoryRepository.findAll().forEach(category ->
                names.put(category.getCategoryCode(), category.getCategoryName()));
        return names;
    }

    private FollowupCategory requireCategory(String categoryCode) {
        return categoryRepository.findByCategoryCode(categoryCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "随访分类不存在"));
    }

    private FollowupTemplate requireTemplate(String templateCode) {
        return templateRepository.findByTemplateCode(templateCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "随访模板不存在"));
    }

    private FollowupPlan requirePlan(String planNo) {
        return planRepository.findByPlanNo(planNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "随访计划不存在"));
    }

    private void validateChannel(String channel) {
        if (!CHANNELS.contains(channel)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的随访渠道");
        }
    }

    private void validateVisitType(String visitType) {
        if (!VISIT_TYPES.contains(visitType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "回访板块只允许门诊/住院/手术/体检/营销");
        }
    }

    private void validateTriggerType(String triggerType) {
        if (!TRIGGER_TYPES.contains(triggerType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的随访触发类型");
        }
    }

    private void validateOffset(Integer offsetDays) {
        if (offsetDays == null || offsetDays < 0 || offsetDays > 365) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "随访偏移天数需在 0 到 365 之间");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || !CONFIG_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态只允许 active 或 disabled");
        }
        return status;
    }

    private FollowupDtos.CategoryView toCategoryView(FollowupCategory category) {
        return new FollowupDtos.CategoryView(category.getCategoryCode(), category.getCategoryName(),
                category.getVisitType(), category.getDescription(), category.getStatus(), category.getSortNo());
    }

    private FollowupDtos.TemplateView toTemplateView(FollowupTemplate template) {
        return new FollowupDtos.TemplateView(template.getTemplateCode(), template.getCategoryCode(),
                template.getTemplateName(), template.getChannel(), template.getQuestions(),
                template.getRiskRules(), template.getVersion(), template.getStatus(), template.getDescription());
    }

    private FollowupDtos.PlanView toPlanView(FollowupPlan plan, Map<String, String> categoryNames) {
        String assigneeName = plan.getDefaultAssigneeId() == null ? null
                : userRepository.findById(plan.getDefaultAssigneeId())
                        .map(SysUser::getDisplayName).orElse(null);
        return new FollowupDtos.PlanView(plan.getPlanNo(), plan.getPlanName(), plan.getCategoryCode(),
                categoryNames.get(plan.getCategoryCode()), plan.getTemplateCode(), plan.getTriggerType(),
                plan.getOffsetDays(), plan.getChannel(), plan.getDefaultAssigneeId(), assigneeName,
                plan.getStatus(), plan.getDescription());
    }

    /** 供任务服务复用：按编码读取启用中的分类。 */
    @Transactional(readOnly = true)
    public FollowupCategory requireActiveCategory(String categoryCode) {
        FollowupCategory category = requireCategory(categoryCode);
        if (!"active".equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "随访分类已停用");
        }
        return category;
    }

    /** 供任务服务复用：按编码读取启用中的模板。 */
    @Transactional(readOnly = true)
    public FollowupTemplate requireActiveTemplate(String templateCode) {
        FollowupTemplate template = requireTemplate(templateCode);
        if (!"active".equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "随访模板已停用");
        }
        return template;
    }
}
