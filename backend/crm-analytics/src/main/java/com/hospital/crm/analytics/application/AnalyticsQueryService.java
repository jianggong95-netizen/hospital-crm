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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 运营分析核心查询服务：看板与报表数据计算，统一落实组织数据权限与时间范围追溯。
 *
 * <p>数据权限规则：全院范围直接聚合；非全院范围先解析 allowedUserIds
 * （sys_user 中 org_id 属于允许组织的用户 ∪ 本人），再按各表归属列过滤；
 * 无归属列的指标（客户、订单、短信）在非全院范围返回 value=null + scopeNote。</p>
 */
@Service
public class AnalyticsQueryService {

    private static final String UNSUPPORTED_NOTE = "当前数据范围不支持该指标，需全院权限";
    private static final String CUSTOM_NOTE = "自定义指标暂未接入自动计算，请按口径说明人工核对";
    private static final long MAX_RANGE_DAYS = 366;

    private final DataScopeService dataScopeService;
    private final MetricDefinitionRepository metricRepository;
    private final ReportDefinitionRepository reportRepository;
    private final MetricAggRepository aggRepository;

    public AnalyticsQueryService(DataScopeService dataScopeService,
                                 MetricDefinitionRepository metricRepository,
                                 ReportDefinitionRepository reportRepository,
                                 MetricAggRepository aggRepository) {
        this.dataScopeService = dataScopeService;
        this.metricRepository = metricRepository;
        this.reportRepository = reportRepository;
        this.aggRepository = aggRepository;
    }

    // ------------------------------------------------------------------
    // 看板
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public AnalyticsDtos.DashboardView dashboard(LocalDate from, LocalDate to) {
        validateRange(from, to);
        DataScope scope = dataScopeService.current();
        return computeDashboard(scope, from, to);
    }

    /** 按给定数据范围计算看板（导出审批按全院口径复用时调用）。 */
    AnalyticsDtos.DashboardView computeDashboard(DataScope scope, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Range range = toRange(from, to);
        List<MetricDefinition> metrics = metricRepository.findByStatusOrderByMetricCodeAsc("enabled");

        // null 表示全院口径（不拼接范围条件）；非 null 空集合表示受限且无可见数据
        List<UUID> orgIds = scope.all() ? null : new ArrayList<>(scope.orgIds());
        List<UUID> allowedUserIds = null;
        if (!scope.all()) {
            List<UUID> users = new ArrayList<>(aggRepository.findUserIdsByOrgs(orgIds));
            if (scope.includeSelf() && scope.userId() != null && !users.contains(scope.userId())) {
                users.add(scope.userId());
            }
            allowedUserIds = users;
        }

        List<AnalyticsDtos.DashboardCard> cards = new ArrayList<>();
        for (MetricDefinition metric : metrics) {
            cards.add(computeCard(metric, range, scope.all(), allowedUserIds, orgIds));
        }
        return new AnalyticsDtos.DashboardView(from, to, Instant.now(), scope.all(), cards);
    }

    // ------------------------------------------------------------------
    // 报表数据
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public AnalyticsDtos.ReportDataView reportData(String reportCode, LocalDate from, LocalDate to) {
        validateRange(from, to);
        ReportDefinition report = requireEnabledReport(reportCode);
        DataScope scope = dataScopeService.current();
        return buildReportData(report, from, to, scope);
    }

    /** 按给定数据范围构建报表数据；导出审批以全院口径（scope.all=true）复用。 */
    public AnalyticsDtos.ReportDataView buildReportData(ReportDefinition report,
                                                        LocalDate from, LocalDate to,
                                                        DataScope scope) {
        validateRange(from, to);
        if ("customer_detail".equals(report.getReportType())) {
            return buildCustomerDetail(report, from, to, scope);
        }
        return buildMetricSummary(report, from, to, scope);
    }

    private AnalyticsDtos.ReportDataView buildMetricSummary(ReportDefinition report,
                                                            LocalDate from, LocalDate to,
                                                            DataScope scope) {
        AnalyticsDtos.DashboardView dashboard = computeDashboard(scope, from, to);
        List<AnalyticsDtos.ReportColumn> columns = List.of(
                new AnalyticsDtos.ReportColumn("code", "指标编码"),
                new AnalyticsDtos.ReportColumn("name", "指标名称"),
                new AnalyticsDtos.ReportColumn("value", "指标值"),
                new AnalyticsDtos.ReportColumn("unit", "单位"),
                new AnalyticsDtos.ReportColumn("caliber", "口径说明"),
                new AnalyticsDtos.ReportColumn("scope_note", "数据范围说明"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AnalyticsDtos.DashboardCard card : dashboard.cards()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", card.metricCode());
            row.put("name", card.metricName());
            row.put("value", card.value());
            row.put("unit", card.unit());
            row.put("caliber", card.caliberDescription());
            row.put("scope_note", card.scopeNote());
            rows.add(row);
        }
        return new AnalyticsDtos.ReportDataView(report.getReportCode(), report.getReportName(),
                from, to, Instant.now(), scope.all(), columns, rows, rows.size());
    }

    private AnalyticsDtos.ReportDataView buildCustomerDetail(ReportDefinition report,
                                                             LocalDate from, LocalDate to,
                                                             DataScope scope) {
        if (!scope.all()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "客户明细报表无组织归属字段，需要全院数据权限");
        }
        Range range = toRange(from, to);
        List<Map<String, Object>> rows = aggRepository.customerDetail(range.from(), range.toExclusive());
        List<AnalyticsDtos.ReportColumn> columns = List.of(
                new AnalyticsDtos.ReportColumn("customer_no", "客户编号"),
                new AnalyticsDtos.ReportColumn("name", "姓名"),
                new AnalyticsDtos.ReportColumn("phone_masked", "手机号(脱敏)"),
                new AnalyticsDtos.ReportColumn("id_no_masked", "证件号(脱敏)"),
                new AnalyticsDtos.ReportColumn("source_code", "来源渠道"),
                new AnalyticsDtos.ReportColumn("status", "状态"),
                new AnalyticsDtos.ReportColumn("created_at", "创建时间"));
        return new AnalyticsDtos.ReportDataView(report.getReportCode(), report.getReportName(),
                from, to, Instant.now(), true, columns, rows, rows.size());
    }

    // ------------------------------------------------------------------
    // 指标计算
    // ------------------------------------------------------------------
    private AnalyticsDtos.DashboardCard computeCard(MetricDefinition metric, Range range,
                                                    boolean all, List<UUID> allowedUserIds,
                                                    List<UUID> orgIds) {
        BigDecimal value;
        String scopeNote = null;
        String code = metric.getMetricCode();
        switch (code) {
            case "new_customer_count" -> {
                if (!all) {
                    value = null;
                    scopeNote = UNSUPPORTED_NOTE;
                } else {
                    value = BigDecimal.valueOf(aggRepository.countNewCustomers(range.from(), range.to()));
                }
            }
            case "customer_total" -> {
                if (!all) {
                    value = null;
                    scopeNote = UNSUPPORTED_NOTE;
                } else {
                    value = BigDecimal.valueOf(aggRepository.countActiveCustomersUntil(range.to()));
                }
            }
            case "order_paid_amount" -> {
                if (!all) {
                    value = null;
                    scopeNote = UNSUPPORTED_NOTE;
                } else {
                    value = aggRepository.sumOrderPaidAmount(range.from(), range.to())
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            case "sms_delivery_rate" -> {
                if (!all) {
                    value = null;
                    scopeNote = UNSUPPORTED_NOTE;
                } else {
                    Map<String, Object> row = aggRepository.smsCounts(range.from(), range.to());
                    value = rate(row, "sent", "total");
                }
            }
            case "lead_count" -> {
                Map<String, Object> row = aggRepository.leadCounts(
                        range.from(), range.to(), allowedUserIds, orgIds);
                value = count(row, "total");
            }
            case "lead_conversion_rate" -> {
                Map<String, Object> row = aggRepository.leadCounts(
                        range.from(), range.to(), allowedUserIds, orgIds);
                value = rate(row, "converted", "total");
            }
            case "appointment_count" -> {
                Map<String, Object> row = aggRepository.appointmentCounts(
                        range.from(), range.to(), orgIds);
                value = count(row, "total");
            }
            case "no_show_rate" -> {
                Map<String, Object> row = aggRepository.appointmentCounts(
                        range.from(), range.to(), orgIds);
                value = rate(row, "no_show", "due");
            }
            case "followup_completion_rate" -> {
                Map<String, Object> row = aggRepository.followupCounts(
                        range.from(), range.to(), allowedUserIds);
                value = rate(row, "completed", "due");
            }
            case "complaint_count" ->
                    value = BigDecimal.valueOf(aggRepository.countComplaints(
                            range.from(), range.to(), allowedUserIds));
            case "call_connected_rate" -> {
                Map<String, Object> row = aggRepository.callCounts(
                        range.from(), range.to(), allowedUserIds);
                value = rate(row, "connected", "total");
            }
            case "insurance_received_amount" ->
                    value = aggRepository.sumInsuranceReceived(
                            range.from(), range.to(), allowedUserIds)
                            .setScale(2, RoundingMode.HALF_UP);
            default -> {
                value = null;
                scopeNote = CUSTOM_NOTE;
            }
        }
        return new AnalyticsDtos.DashboardCard(
                metric.getMetricCode(),
                metric.getMetricName(),
                metric.getUnit(),
                value,
                metric.getCaliberDescription(),
                scopeNote);
    }

    private BigDecimal count(Map<String, Object> row, String key) {
        Object raw = row.get(key);
        return raw == null ? BigDecimal.ZERO : new BigDecimal(raw.toString());
    }

    /** 比率 = 分子 ÷ 分母 × 100，保留两位小数；分母为 0 时不计算（value=null）。 */
    private BigDecimal rate(Map<String, Object> row, String numeratorKey, String denominatorKey) {
        Object denominator = row.get(denominatorKey);
        Object numerator = row.get(numeratorKey);
        if (denominator == null || new BigDecimal(denominator.toString()).signum() == 0) {
            return null;
        }
        BigDecimal numeratorValue = numerator == null ? BigDecimal.ZERO
                : new BigDecimal(numerator.toString());
        return numeratorValue.multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(denominator.toString()), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 时间范围与校验
    // ------------------------------------------------------------------
    /** 校验时间区间：to 不早于 from，跨度不超过 366 天。 */
    public void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "时间范围不能为空");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结束日期不能早于起始日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "时间跨度不能超过 366 天");
        }
    }

    ReportDefinition requireEnabledReport(String reportCode) {
        ReportDefinition report = reportRepository.findByReportCode(reportCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表不存在"));
        if (!"enabled".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "报表已停用，不能查询数据");
        }
        return report;
    }

    /** 全院数据范围常量（导出审批按全院口径生成脱敏数据时使用）。 */
    static DataScope allScope() {
        return new DataScope(true, Set.of(), false, null);
    }

    private Range toRange(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return new Range(start, end);
    }

    private record Range(OffsetDateTime from, OffsetDateTime toExclusive) {
        OffsetDateTime to() {
            return toExclusive;
        }
    }
}
