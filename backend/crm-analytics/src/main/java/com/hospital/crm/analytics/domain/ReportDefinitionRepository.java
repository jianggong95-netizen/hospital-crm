package com.hospital.crm.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReportDefinitionRepository
        extends JpaRepository<ReportDefinition, UUID>, JpaSpecificationExecutor<ReportDefinition> {

    boolean existsByReportCode(String reportCode);

    Optional<ReportDefinition> findByReportCode(String reportCode);

    /** 状态变更走条件更新，同步刷新 updated_at，避免脏检查遗漏。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReportDefinition r set r.status = :status, r.updatedAt = :now where r.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("now") Instant now);
}
