package com.hospital.crm.analytics.application;

import com.hospital.crm.analytics.api.AnalyticsDtos;
import com.hospital.crm.analytics.domain.ExportRequest;
import com.hospital.crm.analytics.domain.ExportRequestRepository;
import com.hospital.crm.analytics.domain.ReportDefinition;
import com.hospital.crm.analytics.domain.ReportDefinitionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导出申请：审批仅 pending 可批并生成脱敏 CSV 与 64 位哈希、驳回必填原因、
 * 下载未批准 422、非申请人且无审批权限 403、CSV 不含 11 位连续明文手机号。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceTest {

    private static final UUID EXPORT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);

    @Mock
    private ExportRequestRepository exportRepository;
    @Mock
    private ReportDefinitionRepository reportRepository;
    @Mock
    private AnalyticsQueryService analyticsQueryService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private ExportService service;

    @BeforeEach
    void setUp() {
        service = new ExportService(exportRepository, reportRepository,
                analyticsQueryService, userRepository, auditService);
        when(exportRepository.save(any(ExportRequest.class))).thenAnswer(invocation -> {
            ExportRequest entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", EXPORT_ID);
            }
            return entity;
        });
    }

    private ExportRequest pending() {
        ExportRequest entity = new ExportRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", EXPORT_ID);
        entity.setReportCode("RPT_CUSTOMER_DETAIL");
        entity.setRequesterUserId(REQUESTER);
        entity.setDataFrom(FROM);
        entity.setDataTo(TO);
        entity.setReason("月度经营分析");
        entity.setStatus("pending");
        return entity;
    }

    private ReportDefinition customerDetailReport() {
        ReportDefinition report = new ReportDefinition();
        report.setReportCode("RPT_CUSTOMER_DETAIL");
        report.setReportName("客户明细导出");
        report.setReportType("customer_detail");
        report.setStatus("enabled");
        return report;
    }

    /** 与在线查询同口径的报表数据（仅脱敏列）。 */
    private AnalyticsDtos.ReportDataView maskedReportData() {
        List<AnalyticsDtos.ReportColumn> columns = List.of(
                new AnalyticsDtos.ReportColumn("customer_no", "客户编号"),
                new AnalyticsDtos.ReportColumn("name", "姓名"),
                new AnalyticsDtos.ReportColumn("phone_masked", "手机号(脱敏)"),
                new AnalyticsDtos.ReportColumn("id_no_masked", "证件号(脱敏)"),
                new AnalyticsDtos.ReportColumn("source_code", "来源渠道"),
                new AnalyticsDtos.ReportColumn("status", "状态"),
                new AnalyticsDtos.ReportColumn("created_at", "创建时间"));
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("customer_no", "C100001");
        row.put("name", "张三");
        row.put("phone_masked", "138****1111");
        row.put("id_no_masked", "110101********1234");
        row.put("source_code", "ONLINE");
        row.put("status", "active");
        row.put("created_at", "2026-01-05 10:20:30");
        return new AnalyticsDtos.ReportDataView("RPT_CUSTOMER_DETAIL", "客户明细导出",
                FROM, TO, java.time.Instant.now(), true, columns, List.of(row), 1);
    }

    @Test
    void approveOnlyAllowedForPending() {
        ExportRequest entity = pending();
        entity.setStatus("approved");
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.approve(EXPORT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该导出申请已处理")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void approveGeneratesMaskedCsvWithSha256Hash() {
        ExportRequest entity = pending();
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(entity));
        when(analyticsQueryService.requireEnabledReport("RPT_CUSTOMER_DETAIL"))
                .thenReturn(customerDetailReport());
        when(analyticsQueryService.buildReportData(any(), eq(FROM), eq(TO), any()))
                .thenReturn(maskedReportData());

        try (MockedStatic<CurrentUser> currentUser = mockStatic(CurrentUser.class)) {
            currentUser.when(CurrentUser::id).thenReturn(APPROVER);

            service.approve(EXPORT_ID);
        }

        ArgumentCaptor<ExportRequest> captor = ArgumentCaptor.forClass(ExportRequest.class);
        verify(exportRepository).save(captor.capture());
        ExportRequest saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("approved");
        assertThat(saved.getApproverUserId()).isEqualTo(APPROVER);
        assertThat(saved.getApprovedAt()).isNotNull();
        assertThat(saved.getRowCount()).isEqualTo(1);
        assertThat(saved.getFileHash()).hasSize(64).matches("[0-9a-f]{64}");

        String csv = saved.getCsvContent();
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("# 数据时间范围：2026-01-01 至 2026-01-31");
        assertThat(csv).contains("手机号(脱敏)");
        assertThat(csv).contains("138****1111");
        // 验收点：导出脱敏，CSV 中不得出现 11 位连续数字（明文手机号）
        assertThat(csv).doesNotContainPattern("\\d{11}");
        verify(auditService).record(eq("EXPORT_APPROVE"), any(), eq(EXPORT_ID.toString()), any());
    }

    @Test
    void csvUsesSameReportDataAsOnlineQuery() {
        ExportRequest entity = pending();
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(entity));
        when(analyticsQueryService.requireEnabledReport("RPT_CUSTOMER_DETAIL"))
                .thenReturn(customerDetailReport());
        when(analyticsQueryService.buildReportData(any(), any(), any(), any()))
                .thenReturn(maskedReportData());

        try (MockedStatic<CurrentUser> currentUser = mockStatic(CurrentUser.class)) {
            currentUser.when(CurrentUser::id).thenReturn(APPROVER);
            service.approve(EXPORT_ID);
        }

        verify(analyticsQueryService).buildReportData(any(), eq(FROM), eq(TO), any());
    }

    @Test
    void rejectRequiresReason() {
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.reject(EXPORT_ID, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回原因不能为空")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void rejectOnlyAllowedForPending() {
        ExportRequest entity = pending();
        entity.setStatus("rejected");
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.reject(EXPORT_ID, "重复申请"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该导出申请已处理")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void downloadRejectsWhenNotApproved() {
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.download(EXPORT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出尚未批准")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void downloadForbiddenForStrangerWithoutApprovePermission() {
        ExportRequest entity = pending();
        entity.setStatus("approved");
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(entity));

        try (MockedStatic<CurrentUser> currentUser = mockStatic(CurrentUser.class)) {
            currentUser.when(CurrentUser::id).thenReturn(OTHER);
            currentUser.when(() -> CurrentUser.hasPermission("crm:analytics:export:approve"))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.download(EXPORT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅申请人或审批人可下载")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Test
    void downloadAllowedForRequester() {
        ExportRequest entity = pending();
        entity.setStatus("approved");
        entity.setCsvContent("csv-body");
        when(exportRepository.findById(EXPORT_ID)).thenReturn(Optional.of(entity));

        try (MockedStatic<CurrentUser> currentUser = mockStatic(CurrentUser.class)) {
            currentUser.when(CurrentUser::id).thenReturn(REQUESTER);

            AnalyticsDtos.ExportFile file = service.download(EXPORT_ID);

            assertThat(file.content()).isEqualTo("csv-body");
            assertThat(file.fileName()).startsWith("export-RPT_CUSTOMER_DETAIL-")
                    .endsWith(".csv");
        }
    }

    @Test
    void createValidatesReportAndRange() {
        when(analyticsQueryService.requireEnabledReport("RPT_CUSTOMER_DETAIL"))
                .thenReturn(customerDetailReport());

        try (MockedStatic<CurrentUser> currentUser = mockStatic(CurrentUser.class)) {
            currentUser.when(CurrentUser::id).thenReturn(REQUESTER);

            AnalyticsDtos.ExportView view = service.create(new AnalyticsDtos.ExportCreateRequest(
                    "RPT_CUSTOMER_DETAIL", FROM, TO, "季度复盘"));

            assertThat(view.status()).isEqualTo("pending");
            assertThat(view.requesterUserId()).isEqualTo(REQUESTER);
        }
        verify(auditService).record(eq("EXPORT_REQUEST"), any(), any(), any());
    }
}
