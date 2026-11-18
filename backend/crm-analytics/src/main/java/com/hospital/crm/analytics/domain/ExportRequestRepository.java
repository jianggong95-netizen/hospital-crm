package com.hospital.crm.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ExportRequestRepository
        extends JpaRepository<ExportRequest, UUID>, JpaSpecificationExecutor<ExportRequest> {
}
