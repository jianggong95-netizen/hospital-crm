package com.hospital.crm.insurance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InsuranceEmailAttachmentRepository extends JpaRepository<InsuranceEmailAttachment, UUID> {

    List<InsuranceEmailAttachment> findByEmailIdInOrderByCreatedAtAsc(Collection<UUID> emailIds);
}
