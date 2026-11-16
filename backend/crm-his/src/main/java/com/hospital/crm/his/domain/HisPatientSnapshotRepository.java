package com.hospital.crm.his.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HisPatientSnapshotRepository extends JpaRepository<HisPatientSnapshot, UUID> {

    Optional<HisPatientSnapshot> findBySourceSystemAndExternalPatientId(
            String sourceSystem, String externalPatientId);
}
