package com.hospital.crm.analytics.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 运营分析接口契约 DTO。看板与报表结果均携带 dataFrom/dataTo/generatedAt，
 * 保证结果可追溯数据时间范围；导出仅输出脱敏列。
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    // ------------------------------------------------------------------
    // 指标定义
    // ------------------------------------------------------------------
    public record MetricView(
            UUID id,
            String metricCode,
            String metricName,
            String category,
            String caliberDescription,
            String dataSource,
            String unit,
            Integer decimalPlaces,
            String status,
            String remark,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateMetricRequest(
            @NotBlank(message = "指标编码不能为空")
            @Size(max = 64, message = "指标编码长度不能超过 64")
            String metricCode,

            @NotBlank(message = "指标名称不能为空")
            @Size(max = 120, message = "指标名称长度不能超过 120")
            String metricName,

            @NotBlank(message = "指标分类不能为空")
            @Pattern(regexp = "customer|operation|appointment|revenue|followup|complaint|callcenter|insurance",
                    message = "指标分类取值不合法")
            String category,

            @NotBlank(message = "指标口径说明不能为空")
            String caliberDescription,

            @NotBlank(message = "数据来源不能为空")
            @Size(max = 120, message = "数据来源长度不能超过 120")
            String dataSource,

            @Size(max = 20, message = "单位长度不能超过 20")
            String unit,

            Integer decimalPlaces,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record UpdateMetricRequest(
            @NotBlank(message = "指标名称不能为空")
            @Size(max = 120, message = "指标名称长度不能超过 120")
            String metricName,

            @NotBlank(message = "指标分类不能为空")
            @Pattern(regexp = "customer|operation|appointment|revenue|followup|complaint|callcenter|insurance",
                    message = "指标分类取值不合法")
            String category,

            @NotBlank(message = "指标口径说明不能为空")
            String caliberDescription,

            @NotBlank(message = "数据来源不能为空")
            @Size(max = 120, message = "数据来源长度不能超过 120")
            String dataSource,

            @Size(max = 20, message = "单位长度不能超过 20")
            String unit,

            Integer decimalPlaces,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record StatusRequest(
            @NotBlank(message = "状态不能为空")
            String status
    ) {
    }

    // ------------------------------------------------------------------
    // 看板
    // ------------------------------------------------------------------
    public record DashboardView(
            LocalDate dataFrom,
            LocalDate dataTo,
            Instant generatedAt,
            boolean scopeAll,
            List<DashboardCard> cards
    ) {
    }

    public record DashboardCard(
            String metricCode,
            String metricName,
            String unit,
            BigDecimal value,
            String caliberDescription,
            String scopeNote
    ) {
    }

    // ------------------------------------------------------------------
    // 报表定义与数据
    // ------------------------------------------------------------------
    public record ReportView(
            UUID id,
            String reportCode,
            String reportName,
            String description,
            String reportType,
            List<String> metricCodes,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateReportRequest(
            @NotBlank(message = "报表编码不能为空")
            @Size(max = 64, message = "报表编码长度不能超过 64")
            String reportCode,

            @NotBlank(message = "报表名称不能为空")
            @Size(max = 120, message = "报表名称长度不能超过 120")
            String reportName,

            @Size(max = 500, message = "描述长度不能超过 500")
            String description,

            @NotBlank(message = "报表类型不能为空")
            @Pattern(regexp = "metric_summary|customer_detail", message = "报表类型取值不合法")
            String reportType,

            List<String> metricCodes
    ) {
    }

    public record ReportDataView(
            String reportCode,
            String reportName,
            LocalDate dataFrom,
            LocalDate dataTo,
            Instant generatedAt,
            boolean scopeAll,
            List<ReportColumn> columns,
            List<Map<String, Object>> rows,
            int rowCount
    ) {
    }

    public record ReportColumn(
            String key,
            String label
    ) {
    }

    // ------------------------------------------------------------------
    // 导出申请
    // ------------------------------------------------------------------
    public record ExportView(
            UUID id,
            String reportCode,
            String reportName,
            UUID requesterUserId,
            String requesterName,
            LocalDate dataFrom,
            LocalDate dataTo,
            String reason,
            String status,
            String approverName,
            Instant approvedAt,
            String rejectReason,
            Integer rowCount,
            String fileHash,
            Instant createdAt
    ) {
    }

    public record ExportCreateRequest(
            @NotBlank(message = "报表编码不能为空")
            String reportCode,

            @NotNull(message = "数据起始日期不能为空")
            LocalDate dataFrom,

            @NotNull(message = "数据结束日期不能为空")
            LocalDate dataTo,

            @NotBlank(message = "导出原因不能为空")
            @Size(max = 500, message = "导出原因长度不能超过 500")
            String reason
    ) {
    }

    public record ExportRejectRequest(
            @NotBlank(message = "驳回原因不能为空")
            @Size(max = 500, message = "驳回原因长度不能超过 500")
            String reason
    ) {
    }

    /** 导出文件下载载体：文件名 + CSV 内容。 */
    public record ExportFile(
            String fileName,
            String content
    ) {
    }
}
