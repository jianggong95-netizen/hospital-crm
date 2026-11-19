package com.hospital.crm.ops.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpsJobRunRepository extends JpaRepository<OpsJobRun, UUID> {

    List<OpsJobRun> findTop20ByJobCodeOrderByStartedAtDesc(String jobCode);

    Optional<OpsJobRun> findTop1ByJobCodeOrderByStartedAtDesc(String jobCode);

    Optional<OpsJobRun> findTop1ByJobCodeAndStatusOrderByStartedAtDesc(String jobCode, String status);

    @Transactional
    @Modifying
    @Query("delete from OpsJobRun r where r.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") Instant before);
}
