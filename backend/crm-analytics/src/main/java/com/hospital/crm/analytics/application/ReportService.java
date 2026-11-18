package com.hospital.crm.analytics.application;

import com.hospital.crm.analytics.api.AnalyticsDtos;
import com.hospital.crm.analytics.domain.ReportDefinition;
import com.hospital.crm.analytics.domain.ReportDefinitionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 报表定义服务：分页查询、创建与启停。
 * metric_summary 报表关联一组指标编码；customer_detail 输出脱敏客户明细。
 */
@Service
public class ReportService {

    private static final String RESOURCE = "ANALYTICS_REPORT";
    private static final Set<String> REPORT_TYPES = Set.of("metric_summary", "customer_detail");
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");

    private final ReportDefinitionRepository reportRepository;
    private final AuditService auditService;

    public ReportService(ReportDefinitionRepository reportRepository, AuditService auditService) {
        this.reportRepository = reportRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalyticsDtos.ReportView> list(String keyword, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<ReportDefinition> result = reportRepository.findAll(
                specification(keyword),
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AnalyticsDtos.ReportView> items = result.getContent().stream()
                .map(this::toView).toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public AnalyticsDtos.ReportView create(AnalyticsDtos.CreateReportRequest request) {
        if (reportRepository.existsByReportCode(request.reportCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "报表编码已存在");
        }
        if (!REPORT_TYPES.contains(request.reportType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "报表类型取值不合法");
        }
        if ("metric_summary".equals(request.reportType())
                && (request.metricCodes() == null || request.metricCodes().isEmpty())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "指标汇总报表必须关联至少一个指标编码");
        }
        ReportDefinition report = new ReportDefinition();
        report.setReportCode(request.reportCode().trim());
        report.setReportName(request.reportName().trim());
        report.setDescription(blankToNull(request.description()));
        report.setReportType(request.reportType());
        report.setMetricCodes(joinMetricCodes(request.metricCodes()));
        ReportDefinition saved = reportRepository.save(report);

        auditService.record("REPORT_CREATE", RESOURCE, saved.getId().toString(),
                java.util.Map.of("reportCode", saved.getReportCode(),
                        "reportType", saved.getReportType()));
        return toView(saved);
    }

    @Transactional
    public AnalyticsDtos.ReportView updateStatus(UUID reportId, String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态取值不合法");
        }
        ReportDefinition report = requireReport(reportId);
        reportRepository.updateStatus(reportId, status, Instant.now());
        ReportDefinition refreshed = requireReport(reportId);

        auditService.record("REPORT_STATUS", RESOURCE, reportId.toString(),
                java.util.Map.of("reportCode", report.getReportCode(), "status", status));
        return toView(refreshed);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    ReportDefinition requireReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报表定义不存在"));
    }

    static List<String> splitMetricCodes(String metricCodes) {
        if (metricCodes == null || metricCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(metricCodes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String joinMetricCodes(List<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return null;
        }
        return String.join(",", metricCodes.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private Specification<ReportDefinition> specification(String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reportCode")), like),
                        cb.like(cb.lower(root.get("reportName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AnalyticsDtos.ReportView toView(ReportDefinition report) {
        return new AnalyticsDtos.ReportView(
                report.getId(),
                report.getReportCode(),
                report.getReportName(),
                report.getDescription(),
                report.getReportType(),
                splitMetricCodes(report.getMetricCodes()),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
