package com.hospital.crm.complaint.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintRectificationRepository extends JpaRepository<ComplaintRectification, UUID> {

    List<ComplaintRectification> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    long countByTicketIdAndStatus(UUID ticketId, String status);
}
