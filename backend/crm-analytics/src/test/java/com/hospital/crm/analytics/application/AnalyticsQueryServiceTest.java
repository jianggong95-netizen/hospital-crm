package com.hospital.crm.analytics.application;

import com.hospital.crm.analytics.api.AnalyticsDtos;
import com.hospital.crm.analytics.domain.MetricAggRepository;
import com.hospital.crm.analytics.domain.MetricDefinition;
import com.hospital.crm.analytics.domain.MetricDefinitionRepository;
import com.hospital.crm.analytics.domain.ReportDefinition;
import com.hospital.crm.analytics.domain.ReportDefinitionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.security.DataScope;
import com.hospital.crm.system.security.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 看板与报表查询：非全院范围无归属指标返回 scopeNote、时间跨度校验、
 * 客户明细非全院范围 403、比率分母为 0 不计算。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsQueryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);

    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private MetricDefinitionRepository metricRepository;
    @Mock
    private ReportDefinitionRepository reportRepository;
    @Mock
    private MetricAggRepository aggRepository;

    private AnalyticsQueryService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsQueryService(dataScopeService, metricRepository,
                reportRepository, aggRepository);
    }

    private MetricDefinition metric(String code, String name) {
        MetricDefinition metric = new MetricDefinition();
        metric.setMetricCode(code);
        metric.setMetricName(name);
        metric.setCategory("customer");
        metric.setCaliberDescription(name + "口径说明");
        metric.setDataSource("crm_x.created_at");
        metric.setUnit("count");
        metric.setDecimalPlaces(0);
        metric.setStatus("enabled");
        return metric;
    }

    @Test
    void dashboardMarksUnsupportedMetricsWhenScopeNotAll() {
        when(dataScopeService.current())
                .thenReturn(new DataScope(false, Set.of(), true, USER_ID));
        when(metricRepository.findByStatusOrderByMetricCodeAsc("enabled")).thenReturn(List.of(
                metric("new_customer_count", "新增客户数"),
                metric("lead_count", "新增线索数")));
        when(aggRepository.findUserIdsByOrgs(anyList())).thenReturn(List.of());
        when(aggRepository.leadCounts(any(), any(), anyList(), anyList()))
                .thenReturn(Map.of("total", 7L, "converted", 2L));

        AnalyticsDtos.DashboardView view = service.dashboard(FROM, TO);

        assertThat(view.scopeAll()).isFalse();
        assertThat(view.dataFrom()).isEqualTo(FROM);
        assertThat(view.dataTo()).isEqualTo(TO);
        assertThat(view.generatedAt()).isNotNull();
        assertThat(view.cards()).hasSize(2);

        AnalyticsDtos.DashboardCard customerCard = view.cards().get(0);
        assertThat(customerCard.metricCode()).isEqualTo("new_customer_count");
        assertThat(customerCard.value()).isNull();
        assertThat(customerCard.scopeNote()).isEqualTo("当前数据范围不支持该指标，需全院权限");

        AnalyticsDtos.DashboardCard leadCard = view.cards().get(1);
        assertThat(leadCard.value()).isEqualByComparingTo("7");
        assertThat(leadCard.scopeNote()).isNull();
    }

    @Test
    void dashboardRateReturnsNullWhenDenominatorZero() {
        when(dataScopeService.current()).thenReturn(new DataScope(true, Set.of(), false, USER_ID));
        when(metricRepository.findByStatusOrderByMetricCodeAsc("enabled"))
                .thenReturn(List.of(metric("lead_conversion_rate", "线索转化率")));
        when(aggRepository.leadCounts(any(), any(), isNull(), isNull()))
                .thenReturn(Map.of("total", 0L, "converted", 0L));

        AnalyticsDtos.DashboardView view = service.dashboard(FROM, TO);

        assertThat(view.cards().get(0).value()).isNull();
    }

    @Test
    void dashboardAllScopePassesNullListsToAggRepository() {
        when(dataScopeService.current()).thenReturn(new DataScope(true, Set.of(), false, USER_ID));
        when(metricRepository.findByStatusOrderByMetricCodeAsc("enabled"))
                .thenReturn(List.of(metric("lead_count", "新增线索数")));
        when(aggRepository.leadCounts(any(), any(), isNull(), isNull()))
                .thenReturn(Map.of("total", 9L, "converted", 3L));

        AnalyticsDtos.DashboardView view = service.dashboard(FROM, TO);

        assertThat(view.scopeAll()).isTrue();
        assertThat(view.cards().get(0).value()).isEqualByComparingTo("9");
        assertThat(view.cards().get(0).scopeNote()).isNull();
    }

    @Test
    void dashboardRejectsRangeOver366Days() {
        assertThatThrownBy(() -> service.dashboard(FROM, FROM.plusDays(367)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("时间跨度不能超过 366 天")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void dashboardRejectsReversedRange() {
        assertThatThrownBy(() -> service.dashboard(TO, FROM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束日期不能早于起始日期")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void customerDetailReportForbiddenWhenScopeNotAll() {
        ReportDefinition report = new ReportDefinition();
        report.setReportCode("RPT_CUSTOMER_DETAIL");
        report.setReportName("客户明细导出");
        report.setReportType("customer_detail");
        report.setStatus("enabled");
        when(reportRepository.findByReportCode("RPT_CUSTOMER_DETAIL")).thenReturn(Optional.of(report));
        when(dataScopeService.current())
                .thenReturn(new DataScope(false, Set.of(), true, USER_ID));

        assertThatThrownBy(() -> service.reportData("RPT_CUSTOMER_DETAIL", FROM, TO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需要全院数据权限")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void metricSummaryReportCarriesTimeRangeAndCaliber() {
        ReportDefinition report = new ReportDefinition();
        report.setReportCode("RPT_METRIC_SUMMARY");
        report.setReportName("运营指标汇总");
        report.setReportType("metric_summary");
        report.setStatus("enabled");
        when(reportRepository.findByReportCode("RPT_METRIC_SUMMARY")).thenReturn(Optional.of(report));
        when(dataScopeService.current()).thenReturn(new DataScope(true, Set.of(), false, USER_ID));
        when(metricRepository.findByStatusOrderByMetricCodeAsc("enabled"))
                .thenReturn(List.of(metric("lead_count", "新增线索数")));
        when(aggRepository.leadCounts(any(), any(), isNull(), isNull()))
                .thenReturn(Map.of("total", 5L, "converted", 1L));

        AnalyticsDtos.ReportDataView view = service.reportData("RPT_METRIC_SUMMARY", FROM, TO);

        assertThat(view.reportCode()).isEqualTo("RPT_METRIC_SUMMARY");
        assertThat(view.dataFrom()).isEqualTo(FROM);
        assertThat(view.dataTo()).isEqualTo(TO);
        assertThat(view.generatedAt()).isNotNull();
        assertThat(view.rowCount()).isEqualTo(1);
        assertThat(view.rows().get(0)).containsEntry("code", "lead_count");
        assertThat(view.rows().get(0).get("caliber")).isEqualTo("新增线索数口径说明");
    }

    @Test
    void disabledReportRejected() {
        ReportDefinition report = new ReportDefinition();
        report.setReportCode("RPT_X");
        report.setReportType("metric_summary");
        report.setStatus("disabled");
        when(reportRepository.findByReportCode("RPT_X")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.reportData("RPT_X", FROM, TO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表已停用")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }
}
