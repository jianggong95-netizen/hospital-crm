package com.hospital.crm.knowledge.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KbReviewRepository extends JpaRepository<KbReview, UUID> {

    List<KbReview> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
