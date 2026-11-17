package com.hospital.crm.appointment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface NoShowRecordRepository extends JpaRepository<NoShowRecord, UUID>, JpaSpecificationExecutor<NoShowRecord> {

    Optional<NoShowRecord> findByAppointmentId(UUID appointmentId);

    long countByCustomerId(UUID customerId);
}
