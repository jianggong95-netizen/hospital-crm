package com.hospital.crm.analytics.api;

import com.hospital.crm.analytics.application.AnalyticsQueryService;
import com.hospital.crm.analytics.application.ExportService;
import com.hospital.crm.analytics.application.MetricService;
import com.hospital.crm.analytics.application.ReportService;
import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 运营分析接口：指标定义、看板、报表定义与数据、导出申请/审批/下载。
 * 所有查询与导出结果均携带数据时间范围与生成时间，可追溯。
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final MetricService metricService;
    private final ReportService reportService;
    private final AnalyticsQueryService analyticsQueryService;
    private final ExportService exportService;

    public AnalyticsController(MetricService metricService,
                               ReportService reportService,
                               AnalyticsQueryService analyticsQueryService,
                               ExportService exportService) {
        this.metricService = metricService;
        this.reportService = reportService;
        this.analyticsQueryService = analyticsQueryService;
        this.exportService = exportService;
    }

    // ---------- 指标定义 ----------

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('crm:analytics:read')")
    public ApiResponse<PageResponse<AnalyticsDtos.MetricView>> listMetrics(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(metricService.list(keyword, status, category, page, pageSize));
    }

    @PostMapping("/metrics")
    @PreAuthorize("hasAuthority('crm:analytics:metric:manage')")
    public ApiResponse<AnalyticsDtos.MetricView> createMetric(
            @Valid @RequestBody AnalyticsDtos.CreateMetricRequest request) {
        return ApiResponse.ok(metricService.create(request));
    }

    @PutMapping("/metrics/{metricId}")
    @PreAuthorize("hasAuthority('crm:analytics:metric:manage')")
    public ApiResponse<AnalyticsDtos.MetricView> updateMetric(
            @PathVariable UUID metricId,
            @Valid @RequestBody AnalyticsDtos.UpdateMetricRequest request) {
        return ApiResponse.ok(metricService.update(metricId, request));
    }

    @PutMapping("/metrics/{metricId}/status")
    @PreAuthorize("hasAuthority('crm:analytics:metric:manage')")
    public ApiResponse<AnalyticsDtos.MetricView> updateMetricStatus(
            @PathVariable UUID metricId,
            @Valid @RequestBody AnalyticsDtos.StatusRequest request) {
        return ApiResponse.ok(metricService.updateStatus(metricId, request.status()));
    }

    // ---------- 看板 ----------

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('crm:analytics:read')")
    public ApiResponse<AnalyticsDtos.DashboardView> dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsQueryService.dashboard(from, to));
    }

    // ---------- 报表定义与数据 ----------

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('crm:analytics:read')")
    public ApiResponse<PageResponse<AnalyticsDtos.ReportView>> listReports(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(reportService.list(keyword, page, pageSize));
    }

    @PostMapping("/reports")
    @PreAuthorize("hasAuthority('crm:analytics:report:manage')")
    public ApiResponse<AnalyticsDtos.ReportView> createReport(
            @Valid @RequestBody AnalyticsDtos.CreateReportRequest request) {
        return ApiResponse.ok(reportService.create(request));
    }

    @PutMapping("/reports/{reportId}/status")
    @PreAuthorize("hasAuthority('crm:analytics:report:manage')")
    public ApiResponse<AnalyticsDtos.ReportView> updateReportStatus(
            @PathVariable UUID reportId,
            @Valid @RequestBody AnalyticsDtos.StatusRequest request) {
        return ApiResponse.ok(reportService.updateStatus(reportId, request.status()));
    }

    @GetMapping("/reports/{reportCode}/data")
    @PreAuthorize("hasAuthority('crm:analytics:read')")
    public ApiResponse<AnalyticsDtos.ReportDataView> reportData(
            @PathVariable String reportCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsQueryService.reportData(reportCode, from, to));
    }

    // ---------- 导出申请与审批 ----------

    @PostMapping("/exports")
    @PreAuthorize("hasAuthority('crm:analytics:export:request')")
    public ApiResponse<AnalyticsDtos.ExportView> createExport(
            @Valid @RequestBody AnalyticsDtos.ExportCreateRequest request) {
        return ApiResponse.ok(exportService.create(request));
    }

    @GetMapping("/exports")
    @PreAuthorize("hasAuthority('crm:analytics:read')")
    public ApiResponse<PageResponse<AnalyticsDtos.ExportView>> listExports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(exportService.list(status, page, pageSize));
    }

    @PutMapping("/exports/{exportId}/approve")
    @PreAuthorize("hasAuthority('crm:analytics:export:approve')")
    public ApiResponse<AnalyticsDtos.ExportView> approveExport(@PathVariable UUID exportId) {
        return ApiResponse.ok(exportService.approve(exportId));
    }

    @PutMapping("/exports/{exportId}/reject")
    @PreAuthorize("hasAuthority('crm:analytics:export:approve')")
    public ApiResponse<AnalyticsDtos.ExportView> rejectExport(
            @PathVariable UUID exportId,
            @Valid @RequestBody AnalyticsDtos.ExportRejectRequest request) {
        return ApiResponse.ok(exportService.reject(exportId, request.reason()));
    }

    @GetMapping("/exports/{exportId}/download")
    @PreAuthorize("hasAuthority('crm:analytics:export:download')")
    public ResponseEntity<String> downloadExport(@PathVariable UUID exportId) {
        AnalyticsDtos.ExportFile file = exportService.download(exportId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + file.fileName())
                .body(file.content());
    }
}
