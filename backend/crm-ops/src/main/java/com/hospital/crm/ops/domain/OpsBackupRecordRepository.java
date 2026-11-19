package com.hospital.crm.ops.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpsBackupRecordRepository extends JpaRepository<OpsBackupRecord, UUID> {

    boolean existsByBackupCode(String backupCode);

    Optional<OpsBackupRecord> findByBackupCode(String backupCode);

    Optional<OpsBackupRecord> findTop1ByStatusOrderByStartedAtDesc(String status);

    Optional<OpsBackupRecord> findTop1ByRestoreVerifiedTrueOrderByRestoreVerifiedAtDesc();

    Optional<OpsBackupRecord> findTop1ByOrderByStartedAtDesc();

    Page<OpsBackupRecord> findByStatus(String status, Pageable pageable);

    List<OpsBackupRecord> findByStartedAtAfterAndStatus(Instant after, String status);
}
