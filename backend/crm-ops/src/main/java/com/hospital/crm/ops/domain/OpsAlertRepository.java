package com.hospital.crm.ops.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpsAlertRepository extends JpaRepository<OpsAlert, UUID> {

    Page<OpsAlert> findByStatus(String status, Pageable pageable);

    Optional<OpsAlert> findFirstByDedupKeyAndStatusIn(String dedupKey, Collection<String> statuses);

    List<OpsAlert> findByStatusInAndAlertCodeIn(Collection<String> statuses, Collection<String> alertCodes);

    long countByStatusInAndSeverity(Collection<String> statuses, String severity);

    long countByStatusIn(Collection<String> statuses);

    List<OpsAlert> findByStatusInOrderByLastSeenAtDesc(Collection<String> statuses);
}
