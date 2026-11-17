package com.hospital.crm.operation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeadConversionRepository extends JpaRepository<LeadConversion, UUID> {

    Optional<LeadConversion> findByLeadId(UUID leadId);

    boolean existsByLeadId(UUID leadId);
}
