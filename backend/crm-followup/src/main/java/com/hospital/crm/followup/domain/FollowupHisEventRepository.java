package com.hospital.crm.followup.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowupHisEventRepository extends JpaRepository<FollowupHisEvent, UUID> {

    Optional<FollowupHisEvent> findByEventKey(String eventKey);

    Page<FollowupHisEvent> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<FollowupHisEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
