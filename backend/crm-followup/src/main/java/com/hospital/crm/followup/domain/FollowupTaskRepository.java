package com.hospital.crm.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface FollowupTaskRepository extends JpaRepository<FollowupTask, UUID>,
        JpaSpecificationExecutor<FollowupTask> {

    @Query(value = "select nextval('crm_followup_task_no_seq')", nativeQuery = true)
    long nextTaskNo();

    Optional<FollowupTask> findByTaskNo(String taskNo);

    boolean existsByPlanIdAndTriggerEventKey(UUID planId, String triggerEventKey);

    long countByStatus(String status);
}
