package com.hospital.crm.his.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface HisSyncLogRepository
        extends JpaRepository<HisSyncLogEntry, UUID>, JpaSpecificationExecutor<HisSyncLogEntry> {
}
