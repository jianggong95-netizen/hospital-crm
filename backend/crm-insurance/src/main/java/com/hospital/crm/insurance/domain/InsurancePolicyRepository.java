package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InsurancePolicyRepository
        extends JpaRepository<InsurancePolicy, UUID>, JpaSpecificationExecutor<InsurancePolicy> {

    Optional<InsurancePolicy> findByPolicyNo(String policyNo);

    boolean existsByPolicyNo(String policyNo);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InsurancePolicy p set p.status = :status, p.updatedAt = :now where p.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("now") Instant now);
}
