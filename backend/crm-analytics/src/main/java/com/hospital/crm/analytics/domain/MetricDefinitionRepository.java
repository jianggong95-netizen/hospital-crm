package com.hospital.crm.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricDefinitionRepository
        extends JpaRepository<MetricDefinition, UUID>, JpaSpecificationExecutor<MetricDefinition> {

    boolean existsByMetricCode(String metricCode);

    Optional<MetricDefinition> findByMetricCode(String metricCode);

    List<MetricDefinition> findByStatusOrderByMetricCodeAsc(String status);

    List<MetricDefinition> findByMetricCodeIn(List<String> metricCodes);

    /** 状态变更走条件更新，同步刷新 updated_at，避免脏检查遗漏。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MetricDefinition m set m.status = :status, m.updatedAt = :now where m.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("now") Instant now);
}
