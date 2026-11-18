package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceCompanyRepository
        extends JpaRepository<InsuranceCompany, UUID>, JpaSpecificationExecutor<InsuranceCompany> {

    Optional<InsuranceCompany> findByCompanyCode(String companyCode);

    boolean existsByCompanyCode(String companyCode);

    /** 状态变更走条件更新，同步刷新 updated_at，避免脏检查遗漏。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InsuranceCompany c set c.status = :status, c.updatedAt = :now where c.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("now") Instant now);
}
