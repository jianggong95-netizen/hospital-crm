package com.hospital.crm.operation.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadContactRecordRepository extends JpaRepository<LeadContactRecord, UUID> {

    Page<LeadContactRecord> findByLeadIdOrderByContactedAtDesc(UUID leadId, Pageable pageable);

    List<LeadContactRecord> findByLeadIdOrderByContactedAtDesc(UUID leadId);
}
