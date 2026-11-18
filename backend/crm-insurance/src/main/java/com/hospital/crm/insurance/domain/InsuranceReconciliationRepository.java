package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceReconciliationRepository
        extends JpaRepository<InsuranceReconciliation, UUID>, JpaSpecificationExecutor<InsuranceReconciliation> {

    Optional<InsuranceReconciliation> findByCompanyIdAndPeriod(UUID companyId, String period);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InsuranceReconciliation r set r.confirmedBy = :confirmedBy, "
            + "r.confirmedAt = :confirmedAt, r.updatedAt = :confirmedAt where r.id = :id")
    int confirm(@Param("id") UUID id, @Param("confirmedBy") UUID confirmedBy,
                @Param("confirmedAt") Instant confirmedAt);
}
