package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceEmailRepository extends JpaRepository<InsuranceEmail, UUID> {

    List<InsuranceEmail> findByCaseIdOrderByCreatedAtAsc(UUID caseId);

    /** 入站邮件按 message_id 幂等去重。 */
    Optional<InsuranceEmail> findByMessageId(String messageId);
}
