package com.hospital.crm.knowledge.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KbVersionRepository extends JpaRepository<KbVersion, UUID> {

    List<KbVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);
}
