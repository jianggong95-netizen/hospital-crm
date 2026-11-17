package com.hospital.crm.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, UUID>,
        JpaSpecificationExecutor<ReconciliationBatch> {

    @Query(value = "select nextval('crm_recon_batch_no_seq')", nativeQuery = true)
    Long nextBatchNo();
}
