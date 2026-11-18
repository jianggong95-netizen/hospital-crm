package com.hospital.crm.complaint.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintFeedbackRepository extends JpaRepository<ComplaintFeedback, UUID> {

    List<ComplaintFeedback> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
