package com.hospital.crm.followup.application;

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
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 随访任务：事件触发按计划生成且重复事件跳过、高风险答案进入人工队列、
 * 状态机与重复提交/重复处置拦截。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowupTaskServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final String TEMPLATE_CODE = "TPL_DISCHARGE_7D";

    @Mock
    private FollowupTaskRepository taskRepository;
    @Mock
    private FollowupRecordRepository recordRepository;
    @Mock
    private FollowupPlanRepository planRepository;
    @Mock
    private FollowupCategoryRepository categoryRepository;
    @Mock
    private FollowupTemplateRepository templateRepository;
    @Mock
    private FollowupConfigService configService;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private FollowupTaskService service;

    @BeforeEach
    void setUp() {
        service = new FollowupTaskService(taskRepository, recordRepository, planRepository,
                categoryRepository, templateRepository, configService, customerService,
                userRepository, auditService);
        when(taskRepository.nextTaskNo()).thenReturn(100001L);
        when(taskRepository.save(any(FollowupTask.class))).thenAnswer(invocation -> {
            FollowupTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
            }
            ReflectionTestUtils.setField(task, "createdAt", Instant.now());
            return task;
        });
        when(customerService.brief(any(UUID.class)))
                .thenReturn(new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", "138****1111", "active"));
        when(customerService.briefs(any()))
                .thenReturn(Map.of(CUSTOMER_ID, new CustomerDtos.CustomerBrief(
                        CUSTOMER_ID, "C001", "张三", "138****1111", "active")));
        when(categoryRepository.findAll()).thenReturn(List.of(category()));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(planRepository.findAllById(any())).thenReturn(List.of());
        when(templateRepository.findByTemplateCode(TEMPLATE_CODE)).thenReturn(Optional.of(template()));
        when(configService.requireActiveCategory(any())).thenReturn(category());
        when(configService.requireActiveTemplate(any())).thenReturn(template());
    }

    @Test
    void createManualTaskSucceeds() {
        FollowupDtos.TaskView view = service.createManual(new FollowupDtos.TaskCreateRequest(
                CUSTOMER_ID, "POST_DISCHARGE", TEMPLATE_CODE, "phone",
                Instant.now().plus(1, ChronoUnit.DAYS), null));

        assertThat(view.taskNo()).isEqualTo("FT100001");
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.customerName()).isEqualTo("张三");
        assertThat(view.categoryName()).isEqualTo("出院随访");
    }

    @Test
    void createManualTaskRejectsTemplateFromAnotherCategory() {
        FollowupTemplate other = template();
        other.setCategoryCode("POST_OP");
        when(configService.requireActiveTemplate(TEMPLATE_CODE)).thenReturn(other);

        assertThatThrownBy(() -> service.createManual(new FollowupDtos.TaskCreateRequest(
                CUSTOMER_ID, "POST_DISCHARGE", TEMPLATE_CODE, "phone",
                Instant.now(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不属于所选随访分类");
    }

    @Test
    void triggerCreatesOneTaskPerActivePlan() {
        FollowupPlan first = plan("FP100001", 1);
        FollowupPlan second = plan("FP100002", 3);
        when(planRepository.findByStatusAndTriggerTypeOrderByPlanNoAsc("active", "SERVICE_COMPLETED"))
                .thenReturn(List.of(first, second));
        when(taskRepository.existsByPlanIdAndTriggerEventKey(any(), eq("EVT-1"))).thenReturn(false);

        Instant occurredAt = Instant.parse("2026-11-01T02:00:00Z");
        FollowupDtos.TriggerResult result = service.trigger(new FollowupDtos.TriggerRequest(
                "SERVICE_COMPLETED", "EVT-1", CUSTOMER_ID, occurredAt));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.tasks()).hasSize(2);
        assertThat(result.tasks().get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void triggerSkipsDuplicatedEvent() {
        FollowupPlan plan = plan("FP100001", 1);
        when(planRepository.findByStatusAndTriggerTypeOrderByPlanNoAsc("active", "ORDER_PAID"))
                .thenReturn(List.of(plan));
        when(taskRepository.existsByPlanIdAndTriggerEventKey(PLAN_ID, "EVT-1")).thenReturn(true);

        FollowupDtos.TriggerResult result = service.trigger(new FollowupDtos.TriggerRequest(
                "ORDER_PAID", "EVT-1", CUSTOMER_ID, Instant.now()));

        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void highRiskAnswersEscalateToManualQueue() {
        FollowupTask task = pendingTask();
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(recordRepository.existsByTaskId(TASK_ID)).thenReturn(false);
        when(recordRepository.findByTaskIdOrderByCreatedAtAsc(TASK_ID)).thenReturn(List.of());

        FollowupDtos.TaskDetail detail = service.submitRecord(TASK_ID,
                new FollowupDtos.RecordCreateRequest(Map.of("condition", "加重"), "DONE", "患者诉症状加重"));

        assertThat(detail.task().status()).isEqualTo("ESCALATED");
        assertThat(detail.task().riskLevel()).isEqualTo("HIGH");
        assertThat(detail.task().escalationRequired()).isTrue();
        assertThat(task.getEscalatedAt()).isNotNull();
    }

    @Test
    void mediumRiskAnswersCompleteWithoutEscalation() {
        FollowupTask task = pendingTask();
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(recordRepository.existsByTaskId(TASK_ID)).thenReturn(false);
        when(recordRepository.findByTaskIdOrderByCreatedAtAsc(TASK_ID)).thenReturn(List.of());

        FollowupDtos.TaskDetail detail = service.submitRecord(TASK_ID,
                new FollowupDtos.RecordCreateRequest(Map.of("medication", "否"), "DONE", null));

        assertThat(detail.task().status()).isEqualTo("COMPLETED");
        assertThat(detail.task().riskLevel()).isEqualTo("MEDIUM");
        assertThat(detail.task().escalationRequired()).isFalse();
    }

    @Test
    void submitRecordRejectsSecondSubmission() {
        FollowupTask task = pendingTask();
        task.setStatus("ESCALATED");
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(recordRepository.existsByTaskId(TASK_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.submitRecord(TASK_ID,
                new FollowupDtos.RecordCreateRequest(Map.of(), "DONE", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交结果");
    }

    @Test
    void submitRecordRejectsCompletedTask() {
        FollowupTask task = pendingTask();
        task.setStatus("COMPLETED");
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.submitRecord(TASK_ID,
                new FollowupDtos.RecordCreateRequest(Map.of(), "DONE", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能提交结果");
    }

    @Test
    void assignRejectsTaskNotPending() {
        FollowupTask task = pendingTask();
        task.setStatus("IN_PROGRESS");
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.assign(TASK_ID, new FollowupDtos.AssignRequest(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅待执行");
    }

    @Test
    void assignRejectsUnknownAssignee() {
        FollowupTask task = pendingTask();
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(userRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> service.assign(TASK_ID, new FollowupDtos.AssignRequest(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("随访人不存在");
    }

    @Test
    void escalateThenResolveMovesTaskBackToCompleted() {
        FollowupTask task = pendingTask();
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        FollowupDtos.TaskView escalated = service.escalate(TASK_ID,
                new FollowupDtos.ReasonRequest("患者情绪激动"));
        assertThat(escalated.status()).isEqualTo("ESCALATED");
        assertThat(escalated.escalationRequired()).isTrue();

        FollowupDtos.TaskView resolved = service.resolve(TASK_ID,
                new FollowupDtos.ResolveRequest("已由主管电话安抚"));
        assertThat(resolved.status()).isEqualTo("COMPLETED");
        assertThat(resolved.completedAt()).isNotNull();
    }

    @Test
    void resolveRejectsTaskNotInManualQueue() {
        FollowupTask task = pendingTask();
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.resolve(TASK_ID, new FollowupDtos.ResolveRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅人工队列");
    }

    @Test
    void cancelRejectsTerminalTask() {
        FollowupTask task = pendingTask();
        task.setStatus("COMPLETED");
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.cancel(TASK_ID, new FollowupDtos.ReasonRequest("取消")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能取消");
    }

    @Test
    void listMapsCustomerCategoryAndStatus() {
        FollowupTask task = pendingTask();
        org.springframework.data.domain.Page<FollowupTask> page =
                new org.springframework.data.domain.PageImpl<>(List.of(task));
        when(taskRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        PageResponse<FollowupDtos.TaskView> response =
                service.list(null, "PENDING", null, null, null, null, null, null, null, 1, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items().get(0).customerName()).isEqualTo("张三");
        assertThat(response.items().get(0).categoryName()).isEqualTo("出院随访");
    }

    private FollowupTask pendingTask() {
        FollowupTask task = new FollowupTask();
        ReflectionTestUtils.setField(task, "id", TASK_ID);
        task.setTaskNo("FT100001");
        task.setCustomerId(CUSTOMER_ID);
        task.setCategoryCode("POST_DISCHARGE");
        task.setTemplateCode(TEMPLATE_CODE);
        task.setChannel("phone");
        task.setPlannedAt(Instant.now());
        task.setStatus("PENDING");
        ReflectionTestUtils.setField(task, "createdAt", Instant.now());
        return task;
    }

    private FollowupCategory category() {
        FollowupCategory category = new FollowupCategory();
        category.setCategoryCode("POST_DISCHARGE");
        category.setCategoryName("出院随访");
        category.setStatus("active");
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    private FollowupTemplate template() {
        FollowupTemplate template = new FollowupTemplate();
        template.setTemplateCode(TEMPLATE_CODE);
        template.setCategoryCode("POST_DISCHARGE");
        template.setTemplateName("出院 7 日随访");
        template.setChannel("phone");
        template.setRiskRules(Map.of(
                "highRisk", List.of(Map.of("questionCode", "condition", "values", List.of("加重"))),
                "mediumRisk", List.of(Map.of("questionCode", "medication", "values", List.of("否")))));
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        return template;
    }

    private FollowupPlan plan(String planNo, int offsetDays) {
        FollowupPlan plan = new FollowupPlan();
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        plan.setPlanNo(planNo);
        plan.setPlanName("测试计划");
        plan.setCategoryCode("POST_DISCHARGE");
        plan.setTemplateCode(TEMPLATE_CODE);
        plan.setTriggerType("SERVICE_COMPLETED");
        plan.setOffsetDays(offsetDays);
        plan.setChannel("phone");
        plan.setStatus("active");
        return plan;
    }
}
