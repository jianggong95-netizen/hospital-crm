package com.hospital.crm.knowledge.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiQueryLogRepository extends JpaRepository<AiQueryLog, UUID> {

    Page<AiQueryLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
