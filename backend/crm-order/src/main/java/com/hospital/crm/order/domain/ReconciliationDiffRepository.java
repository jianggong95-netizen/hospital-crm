package com.hospital.crm.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationDiffRepository extends JpaRepository<ReconciliationDiff, UUID> {

    List<ReconciliationDiff> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
}
