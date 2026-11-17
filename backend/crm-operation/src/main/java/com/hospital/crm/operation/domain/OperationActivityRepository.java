package com.hospital.crm.operation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OperationActivityRepository extends JpaRepository<OperationActivity, UUID>,
        JpaSpecificationExecutor<OperationActivity> {

    Optional<OperationActivity> findByActivityNo(String activityNo);

    @Query(value = "select nextval('crm_activity_no_seq')", nativeQuery = true)
    Long nextActivityNo();
}
