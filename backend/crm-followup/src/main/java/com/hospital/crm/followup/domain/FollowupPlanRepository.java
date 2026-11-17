package com.hospital.crm.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowupPlanRepository extends JpaRepository<FollowupPlan, UUID> {

    @Query(value = "select nextval('crm_followup_plan_no_seq')", nativeQuery = true)
    long nextPlanNo();

    Optional<FollowupPlan> findByPlanNo(String planNo);

    List<FollowupPlan> findAllByOrderByPlanNoAsc();

    List<FollowupPlan> findByStatusAndTriggerTypeOrderByPlanNoAsc(String status, String triggerType);

    List<FollowupPlan> findByStatus(String status);
}
