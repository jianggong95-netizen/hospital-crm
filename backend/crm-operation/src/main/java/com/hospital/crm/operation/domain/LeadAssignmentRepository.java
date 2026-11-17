package com.hospital.crm.operation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadAssignmentRepository extends JpaRepository<LeadAssignment, UUID> {

    List<LeadAssignment> findByLeadIdOrderByAssignedAtDesc(UUID leadId);
}
