package com.hospital.crm.his.application;

import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.trace.TraceContext;
import com.hospital.crm.his.api.HisDtos;
import com.hospital.crm.his.domain.HisSyncLogEntry;
import com.hospital.crm.his.domain.HisSyncLogRepository;
import com.hospital.crm.his.support.HisProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * HIS 同步日志：查询审计留痕与运维排障入口。
 */
@Service
public class HisSyncLogService {

    private static final int MAX_ERROR_CODE = 80;
    private static final int MAX_ERROR_MESSAGE = 1000;
    private static final int MAX_EXTERNAL_ID = 100;

    private final HisSyncLogRepository repository;
    private final HisProperties properties;

    public HisSyncLogService(HisSyncLogRepository repository, HisProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String operation,
                       String externalPatientId,
                       String status,
                       String errorCode,
                       String errorMessage,
                       Instant startedAt,
                       Instant finishedAt) {
        HisSyncLogEntry entry = new HisSyncLogEntry();
        entry.setSourceSystem(properties.getSourceSystem());
        entry.setOperation(truncate(operation, 80));
        entry.setRequestTraceId(truncate(TraceContext.currentTraceId(), 80));
        entry.setExternalPatientId(truncate(externalPatientId, MAX_EXTERNAL_ID));
        entry.setStatus(truncate(status, 20));
        entry.setErrorCode(truncate(errorCode, MAX_ERROR_CODE));
        entry.setErrorMessage(truncate(errorMessage, MAX_ERROR_MESSAGE));
        entry.setStartedAt(startedAt == null ? Instant.now() : startedAt);
        entry.setFinishedAt(finishedAt == null ? Instant.now() : finishedAt);
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<HisDtos.SyncLogItem> page(int page, int pageSize, String status, String operation) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "startedAt"));

        String trimmedStatus = blankToNull(status);
        String trimmedOperation = blankToNull(operation);
        Specification<HisSyncLogEntry> specification = (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (trimmedStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), trimmedStatus));
            }
            if (trimmedOperation != null) {
                predicates.add(criteriaBuilder.equal(root.get("operation"), trimmedOperation));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };

        Page<HisSyncLogEntry> result = repository.findAll(specification, pageable);
        List<HisDtos.SyncLogItem> items = result.getContent().stream().map(this::toItem).toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    private HisDtos.SyncLogItem toItem(HisSyncLogEntry entry) {
        Long durationMs = null;
        if (entry.getStartedAt() != null && entry.getFinishedAt() != null) {
            durationMs = Duration.between(entry.getStartedAt(), entry.getFinishedAt()).toMillis();
        }
        return new HisDtos.SyncLogItem(
                entry.getId(),
                entry.getSourceSystem(),
                entry.getOperation(),
                entry.getRequestTraceId(),
                entry.getExternalPatientId(),
                entry.getStatus(),
                entry.getErrorCode(),
                entry.getErrorMessage(),
                entry.getStartedAt(),
                entry.getFinishedAt(),
                durationMs
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
