package com.hospital.crm.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FollowupRecordRepository extends JpaRepository<FollowupRecord, UUID> {

    List<FollowupRecord> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    boolean existsByTaskId(UUID taskId);
}
