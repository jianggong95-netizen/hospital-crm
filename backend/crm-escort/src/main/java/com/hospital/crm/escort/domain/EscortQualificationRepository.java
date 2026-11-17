package com.hospital.crm.escort.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscortQualificationRepository extends JpaRepository<EscortQualification, UUID> {

    List<EscortQualification> findByEscortIdOrderByIssuedAtDesc(UUID escortId);

    List<EscortQualification> findByEscortIdAndStatus(UUID escortId, String status);

    Optional<EscortQualification> findByIdAndEscortId(UUID id, UUID escortId);

    boolean existsByEscortIdAndQualTypeAndCertNo(UUID escortId, String qualType, String certNo);
}
