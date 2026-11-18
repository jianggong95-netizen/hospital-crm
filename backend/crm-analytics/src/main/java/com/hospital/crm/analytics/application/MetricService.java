package com.hospital.crm.analytics.application;

import com.hospital.crm.analytics.api.AnalyticsDtos;
import com.hospital.crm.analytics.domain.MetricDefinition;
import com.hospital.crm.analytics.domain.MetricDefinitionRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 指标定义服务：分页查询、创建、更新与启停。
 *
 * <p>验收点：每项指标必须维护口径说明；指标编码全局唯一；
 * 每次写操作记录审计（METRIC_CREATE/METRIC_UPDATE/METRIC_STATUS）。</p>
 */
@Service
public class MetricService {

    private static final String RESOURCE = "ANALYTICS_METRIC";
    private static final Set<String> CATEGORIES =
            Set.of("customer", "operation", "appointment", "revenue", "followup",
                    "complaint", "callcenter", "insurance");
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");

    private final MetricDefinitionRepository metricRepository;
    private final AuditService auditService;

    public MetricService(MetricDefinitionRepository metricRepository, AuditService auditService) {
        this.metricRepository = metricRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalyticsDtos.MetricView> list(String keyword, String status, String category,
                                                       int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<MetricDefinition> result = metricRepository.findAll(
                specification(keyword, status, category),
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AnalyticsDtos.MetricView> items = result.getContent().stream()
                .map(this::toView).toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional
    public AnalyticsDtos.MetricView create(AnalyticsDtos.CreateMetricRequest request) {
        if (metricRepository.existsByMetricCode(request.metricCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "指标编码已存在");
        }
        if (!CATEGORIES.contains(request.category())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "指标分类取值不合法");
        }
        MetricDefinition metric = new MetricDefinition();
        metric.setMetricCode(request.metricCode().trim());
        metric.setMetricName(request.metricName().trim());
        metric.setCategory(request.category());
        metric.setCaliberDescription(request.caliberDescription().trim());
        metric.setDataSource(request.dataSource().trim());
        metric.setUnit(blankToNull(request.unit()));
        metric.setDecimalPlaces(request.decimalPlaces() == null ? 0 : request.decimalPlaces());
        metric.setRemark(blankToNull(request.remark()));
        MetricDefinition saved = metricRepository.save(metric);

        auditService.record("METRIC_CREATE", RESOURCE, saved.getId().toString(),
                Map.of("metricCode", saved.getMetricCode(), "metricName", saved.getMetricName()));
        return toView(saved);
    }

    @Transactional
    public AnalyticsDtos.MetricView update(UUID metricId, AnalyticsDtos.UpdateMetricRequest request) {
        MetricDefinition metric = requireMetric(metricId);
        if (!CATEGORIES.contains(request.category())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "指标分类取值不合法");
        }
        metric.setMetricName(request.metricName().trim());
        metric.setCategory(request.category());
        metric.setCaliberDescription(request.caliberDescription().trim());
        metric.setDataSource(request.dataSource().trim());
        metric.setUnit(blankToNull(request.unit()));
        if (request.decimalPlaces() != null) {
            metric.setDecimalPlaces(request.decimalPlaces());
        }
        metric.setRemark(blankToNull(request.remark()));
        MetricDefinition saved = metricRepository.save(metric);

        auditService.record("METRIC_UPDATE", RESOURCE, saved.getId().toString(),
                Map.of("metricCode", saved.getMetricCode()));
        return toView(saved);
    }

    @Transactional
    public AnalyticsDtos.MetricView updateStatus(UUID metricId, String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "状态取值不合法");
        }
        MetricDefinition metric = requireMetric(metricId);
        metricRepository.updateStatus(metricId, status, Instant.now());
        MetricDefinition refreshed = requireMetric(metricId);

        auditService.record("METRIC_STATUS", RESOURCE, metricId.toString(),
                Map.of("metricCode", metric.getMetricCode(), "status", status));
        return toView(refreshed);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    MetricDefinition requireMetric(UUID metricId) {
        return metricRepository.findById(metricId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "指标定义不存在"));
    }

    private Specification<MetricDefinition> specification(String keyword, String status, String category) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("metricCode")), like),
                        cb.like(cb.lower(root.get("metricName")), like)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AnalyticsDtos.MetricView toView(MetricDefinition metric) {
        return new AnalyticsDtos.MetricView(
                metric.getId(),
                metric.getMetricCode(),
                metric.getMetricName(),
                metric.getCategory(),
                metric.getCaliberDescription(),
                metric.getDataSource(),
                metric.getUnit(),
                metric.getDecimalPlaces(),
                metric.getStatus(),
                metric.getRemark(),
                metric.getCreatedAt(),
                metric.getUpdatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
