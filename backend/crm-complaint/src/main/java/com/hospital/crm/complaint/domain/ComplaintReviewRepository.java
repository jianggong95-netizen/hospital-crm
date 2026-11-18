package com.hospital.crm.complaint.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintReviewRepository extends JpaRepository<ComplaintReview, UUID> {

    List<ComplaintReview> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
