package com.hospital.crm.complaint.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintActionRepository extends JpaRepository<ComplaintAction, UUID> {

    List<ComplaintAction> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    long countByTicketId(UUID ticketId);
}
