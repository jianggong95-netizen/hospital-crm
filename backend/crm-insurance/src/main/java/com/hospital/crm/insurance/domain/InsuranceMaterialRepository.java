package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InsuranceMaterialRepository extends JpaRepository<InsuranceMaterial, UUID> {

    List<InsuranceMaterial> findByCaseIdOrderByCreatedAtAsc(UUID caseId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InsuranceMaterial m set m.status = :status where m.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status);
}
