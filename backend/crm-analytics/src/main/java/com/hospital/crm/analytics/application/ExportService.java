package com.hospital.crm.analytics.application;

import com.hospital.crm.analytics.api.AnalyticsDtos;
import com.hospital.crm.analytics.domain.ExportRequest;
import com.hospital.crm.analytics.domain.ExportRequestRepository;
import com.hospital.crm.analytics.domain.ReportDefinition;
import com.hospital.crm.analytics.domain.ReportDefinitionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据导出申请服务：申请、审批（生成脱敏 CSV 与 SHA-256 哈希）、驳回与下载。
 *
 * <p>导出 CSV 与在线查询同口径（复用 AnalyticsQueryService.buildReportData），
 * 首行注释标明数据时间范围与生成时间，客户明细仅含脱敏列；
 * 下载限申请人或审批人，且需 export:download 权限。</p>
 */
@Service
public class ExportService {

    private static final String RESOURCE = "ANALYTICS_EXPORT";
    private static final String PERM_APPROVE = "crm:analytics:export:approve";
    private static final Set<String> EXPORT_STATUSES = Set.of("pending", "approved", "rejected");

    private final ExportRequestRepository exportRepository;
    private final ReportDefinitionRepository reportRepository;
    private final AnalyticsQueryService analyticsQueryService;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public ExportService(ExportRequestRepository exportRepository,
                         ReportDefinitionRepository reportRepository,
                         AnalyticsQueryService analyticsQueryService,
                         SysUserRepository userRepository,
                         AuditService auditService) {
        this.exportRepository = exportRepository;
        this.reportRepository = reportRepository;
        this.analyticsQueryService = analyticsQueryService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 申请
    // ------------------------------------------------------------------
    @Transactional
    public AnalyticsDtos.ExportView create(AnalyticsDtos.ExportCreateRequest request) {
        ReportDefinition report = analyticsQueryService.requireEnabledReport(request.reportCode());
        analyticsQueryService.validateRange(request.dataFrom(), request.dataTo());

        ExportRequest entity = new ExportRequest();
        entity.setReportCode(report.getReportCode());
        entity.setRequesterUserId(CurrentUser.id());
        entity.setDataFrom(request.dataFrom());
        entity.setDataTo(request.dataTo());
        entity.setReason(request.reason().trim());
        entity.setStatus("pending");
        ExportRequest saved = exportRepository.save(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reportCode", saved.getReportCode());
        detail.put("dataFrom", saved.getDataFrom().toString());
        detail.put("dataTo", saved.getDataTo().toString());
        auditService.record("EXPORT_REQUEST", RESOURCE, saved.getId().toString(), detail);
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalyticsDtos.ExportView> list(String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        boolean canSeeAll = CurrentUser.hasPermission(PERM_APPROVE);
        UUID me = CurrentUser.id();
        Page<ExportRequest> result = exportRepository.findAll(
                specification(status, canSeeAll, me),
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(),
                safePage, safePageSize);
    }

    // ------------------------------------------------------------------
    // 审批
    // ------------------------------------------------------------------
    @Transactional
    public AnalyticsDtos.ExportView approve(UUID exportId) {
        ExportRequest entity = requireExport(exportId);
        if (!"pending".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该导出申请已处理");
        }
        ReportDefinition report = analyticsQueryService.requireEnabledReport(entity.getReportCode());
        // 简化口径：审批时按全院数据范围重新计算报表，仅输出脱敏列，CSV 首行注明数据时间范围
        AnalyticsDtos.ReportDataView data = analyticsQueryService.buildReportData(
                report, entity.getDataFrom(), entity.getDataTo(), AnalyticsQueryService.allScope());
        String csv = buildCsv(data);

        entity.setStatus("approved");
        entity.setApproverUserId(CurrentUser.id());
        entity.setApprovedAt(Instant.now());
        entity.setCsvContent(csv);
        entity.setFileHash(sha256Hex(csv));
        entity.setRowCount(data.rowCount());
        ExportRequest saved = exportRepository.save(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reportCode", saved.getReportCode());
        detail.put("rowCount", saved.getRowCount());
        detail.put("fileHash", saved.getFileHash());
        auditService.record("EXPORT_APPROVE", RESOURCE, saved.getId().toString(), detail);
        return toView(saved);
    }

    @Transactional
    public AnalyticsDtos.ExportView reject(UUID exportId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回原因不能为空");
        }
        ExportRequest entity = requireExport(exportId);
        if (!"pending".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该导出申请已处理");
        }
        entity.setStatus("rejected");
        entity.setApproverUserId(CurrentUser.id());
        entity.setApprovedAt(Instant.now());
        entity.setRejectReason(reason.trim());
        ExportRequest saved = exportRepository.save(entity);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reportCode", saved.getReportCode());
        detail.put("reason", saved.getRejectReason());
        auditService.record("EXPORT_REJECT", RESOURCE, saved.getId().toString(), detail);
        return toView(saved);
    }

    // ------------------------------------------------------------------
    // 下载
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public AnalyticsDtos.ExportFile download(UUID exportId) {
        ExportRequest entity = requireExport(exportId);
        if (!"approved".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "导出尚未批准");
        }
        UUID me = CurrentUser.id();
        boolean isRequester = me != null && me.equals(entity.getRequesterUserId());
        if (!isRequester && !CurrentUser.hasPermission(PERM_APPROVE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅申请人或审批人可下载导出文件");
        }
        auditService.record("EXPORT_DOWNLOAD", RESOURCE, entity.getId().toString(),
                Map.of("reportCode", entity.getReportCode()));
        String fileName = "export-" + entity.getReportCode() + "-"
                + entity.getId().toString().substring(0, 8) + ".csv";
        return new AnalyticsDtos.ExportFile(fileName, entity.getCsvContent());
    }

    // ------------------------------------------------------------------
    // CSV 生成
    // ------------------------------------------------------------------
    /** 生成 UTF-8 带 BOM 的 CSV；首行注释标明数据时间范围，保证导出结果可追溯。 */
    String buildCsv(AnalyticsDtos.ReportDataView data) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("# 数据时间范围：").append(data.dataFrom()).append(" 至 ").append(data.dataTo())
                .append("；生成时间：").append(data.generatedAt()).append('\n');
        List<AnalyticsDtos.ReportColumn> columns = data.columns();
        sb.append(columns.stream().map(c -> csvCell(c.label())).collect(Collectors.joining(",")));
        sb.append('\n');
        for (Map<String, Object> row : data.rows()) {
            sb.append(columns.stream()
                    .map(c -> csvCell(row.get(c.key())))
                    .collect(Collectors.joining(",")));
            sb.append('\n');
        }
        return sb.toString();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    ExportRequest requireExport(UUID exportId) {
        return exportRepository.findById(exportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "导出申请不存在"));
    }

    private Specification<ExportRequest> specification(String status, boolean canSeeAll, UUID me) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                if (!EXPORT_STATUSES.contains(status)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出状态取值不合法");
                }
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (!canSeeAll) {
                predicates.add(cb.equal(root.get("requesterUserId"), me));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AnalyticsDtos.ExportView toView(ExportRequest entity) {
        Map<UUID, String> names = resolveNames(List.of(entity));
        return toView(entity, names);
    }

    private List<AnalyticsDtos.ExportView> toViews(List<ExportRequest> entities) {
        Map<UUID, String> names = resolveNames(entities);
        Map<String, String> reportNames = entities.stream()
                .map(ExportRequest::getReportCode)
                .distinct()
                .map(code -> reportRepository.findByReportCode(code).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(ReportDefinition::getReportCode,
                        ReportDefinition::getReportName, (a, b) -> a));
        return entities.stream()
                .map(entity -> toView(entity, names, reportNames.get(entity.getReportCode())))
                .toList();
    }

    private AnalyticsDtos.ExportView toView(ExportRequest entity, Map<UUID, String> names) {
        String reportName = reportRepository.findByReportCode(entity.getReportCode())
                .map(ReportDefinition::getReportName).orElse(null);
        return toView(entity, names, reportName);
    }

    private AnalyticsDtos.ExportView toView(ExportRequest entity, Map<UUID, String> names,
                                            String reportName) {
        return new AnalyticsDtos.ExportView(
                entity.getId(),
                entity.getReportCode(),
                reportName,
                entity.getRequesterUserId(),
                entity.getRequesterUserId() == null ? null : names.get(entity.getRequesterUserId()),
                entity.getDataFrom(),
                entity.getDataTo(),
                entity.getReason(),
                entity.getStatus(),
                entity.getApproverUserId() == null ? null : names.get(entity.getApproverUserId()),
                entity.getApprovedAt(),
                entity.getRejectReason(),
                entity.getRowCount(),
                entity.getFileHash(),
                entity.getCreatedAt());
    }

    private Map<UUID, String> resolveNames(List<ExportRequest> entities) {
        Set<UUID> ids = new HashSet<>();
        for (ExportRequest entity : entities) {
            if (entity.getRequesterUserId() != null) {
                ids.add(entity.getRequesterUserId());
            }
            if (entity.getApproverUserId() != null) {
                ids.add(entity.getApproverUserId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
    }
}
