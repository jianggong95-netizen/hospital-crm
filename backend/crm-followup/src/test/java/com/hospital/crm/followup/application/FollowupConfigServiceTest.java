package com.hospital.crm.followup.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.followup.api.FollowupDtos;
import com.hospital.crm.followup.domain.FollowupCategory;
import com.hospital.crm.followup.domain.FollowupCategoryRepository;
import com.hospital.crm.followup.domain.FollowupPlan;
import com.hospital.crm.followup.domain.FollowupPlanRepository;
import com.hospital.crm.followup.domain.FollowupTemplate;
import com.hospital.crm.followup.domain.FollowupTemplateRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 随访配置：重复编码 409、停用分类不能建模板、模板版本递增、计划分类与模板需一致。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowupConfigServiceTest {

    private static final String CATEGORY_CODE = "POST_DISCHARGE";
    private static final String TEMPLATE_CODE = "TPL_DISCHARGE_7D";

    @Mock
    private FollowupCategoryRepository categoryRepository;
    @Mock
    private FollowupTemplateRepository templateRepository;
    @Mock
    private FollowupPlanRepository planRepository;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private FollowupConfigService service;

    @BeforeEach
    void setUp() {
        service = new FollowupConfigService(categoryRepository, templateRepository, planRepository,
                userRepository, auditService);
        when(categoryRepository.save(any(FollowupCategory.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(templateRepository.save(any(FollowupTemplate.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(planRepository.save(any(FollowupPlan.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(planRepository.nextPlanNo()).thenReturn(100011L);
    }

    @Test
    void createCategoryRejectsDuplicateCode() {
        when(categoryRepository.findByCategoryCode(CATEGORY_CODE))
                .thenReturn(Optional.of(category("active")));

        assertThatThrownBy(() -> service.createCategory(
                new FollowupDtos.CategoryCreateRequest(CATEGORY_CODE, "出院随访", null, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类编码已存在");
    }

    @Test
    void createCategorySucceedsAndAudits() {
        when(categoryRepository.findByCategoryCode(CATEGORY_CODE)).thenReturn(Optional.empty());

        FollowupDtos.CategoryView view = service.createCategory(
                new FollowupDtos.CategoryCreateRequest(CATEGORY_CODE, "出院随访", "说明", 10));

        assertThat(view.categoryCode()).isEqualTo(CATEGORY_CODE);
        assertThat(view.status()).isEqualTo("active");
    }

    @Test
    void createTemplateRejectsDisabledCategory() {
        when(templateRepository.findByTemplateCode(TEMPLATE_CODE)).thenReturn(Optional.empty());
        when(categoryRepository.findByCategoryCode(CATEGORY_CODE))
                .thenReturn(Optional.of(category("disabled")));

        assertThatThrownBy(() -> service.createTemplate(new FollowupDtos.TemplateCreateRequest(
                TEMPLATE_CODE, CATEGORY_CODE, "模板", "phone", List.of(), Map.of(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("随访分类已停用");
    }

    @Test
    void createTemplateRejectsUnsupportedChannel() {
        when(templateRepository.findByTemplateCode(TEMPLATE_CODE)).thenReturn(Optional.empty());
        when(categoryRepository.findByCategoryCode(CATEGORY_CODE)).thenReturn(Optional.of(category("active")));

        assertThatThrownBy(() -> service.createTemplate(new FollowupDtos.TemplateCreateRequest(
                TEMPLATE_CODE, CATEGORY_CODE, "模板", "fax", List.of(), Map.of(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的随访渠道");
    }

    @Test
    void updateTemplateIncrementsVersion() {
        FollowupTemplate template = template(3);
        when(templateRepository.findByTemplateCode(TEMPLATE_CODE)).thenReturn(Optional.of(template));

        FollowupDtos.TemplateView view = service.updateTemplate(TEMPLATE_CODE,
                new FollowupDtos.TemplateUpdateRequest("模板新名称", "phone", List.of(), Map.of(), null));

        assertThat(view.version()).isEqualTo(4);
        assertThat(view.templateName()).isEqualTo("模板新名称");
    }

    @Test
    void createPlanRejectsTemplateFromAnotherCategory() {
        FollowupCategory category = category("active");
        when(categoryRepository.findByCategoryCode("POST_OP")).thenReturn(Optional.of(category));
        FollowupTemplate template = template(1);
        template.setCategoryCode("POST_DISCHARGE");
        when(templateRepository.findByTemplateCode(TEMPLATE_CODE)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.createPlan(new FollowupDtos.PlanCreateRequest(
                "术后随访计划", "POST_OP", TEMPLATE_CODE, "SERVICE_COMPLETED", 3, "phone", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不属于所选随访分类");
    }

    @Test
    void createPlanRejectsUnsupportedTriggerType() {
        when(categoryRepository.findByCategoryCode(CATEGORY_CODE)).thenReturn(Optional.of(category("active")));
        when(templateRepository.findByTemplateCode(TEMPLATE_CODE)).thenReturn(Optional.of(template(1)));

        assertThatThrownBy(() -> service.createPlan(new FollowupDtos.PlanCreateRequest(
                "随访计划", CATEGORY_CODE, TEMPLATE_CODE, "UNKNOWN", 1, "phone", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的随访触发类型");
    }

    @Test
    void changePlanStatusRejectsUnknownStatus() {
        FollowupPlan plan = new FollowupPlan();
        plan.setPlanNo("FP100001");
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        when(planRepository.findByPlanNo("FP100001")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.changePlanStatus("FP100001",
                new FollowupDtos.StatusRequest("paused")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态只允许");
    }

    private FollowupCategory category(String status) {
        FollowupCategory category = new FollowupCategory();
        category.setCategoryCode(CATEGORY_CODE);
        category.setCategoryName("出院随访");
        category.setStatus(status);
        category.setSortNo(10);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    private FollowupTemplate template(int version) {
        FollowupTemplate template = new FollowupTemplate();
        template.setTemplateCode(TEMPLATE_CODE);
        template.setCategoryCode(CATEGORY_CODE);
        template.setTemplateName("出院 7 日随访");
        template.setChannel("phone");
        template.setVersion(version);
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        return template;
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
