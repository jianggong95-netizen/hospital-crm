package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InsuranceEventRepository extends JpaRepository<InsuranceEvent, UUID> {

    List<InsuranceEvent> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
