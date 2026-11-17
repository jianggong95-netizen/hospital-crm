package com.hospital.crm.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowupTemplateRepository extends JpaRepository<FollowupTemplate, UUID> {

    Optional<FollowupTemplate> findByTemplateCode(String templateCode);

    List<FollowupTemplate> findAllByOrderByTemplateCodeAsc();

    List<FollowupTemplate> findByCategoryCodeOrderByTemplateCodeAsc(String categoryCode);

    List<FollowupTemplate> findByStatusOrderByTemplateCodeAsc(String status);
}
