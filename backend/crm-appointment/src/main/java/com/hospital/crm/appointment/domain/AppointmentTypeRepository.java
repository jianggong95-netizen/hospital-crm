package com.hospital.crm.appointment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentTypeRepository extends JpaRepository<AppointmentType, UUID> {

    Optional<AppointmentType> findByTypeCode(String typeCode);

    boolean existsByTypeCode(String typeCode);

    List<AppointmentType> findAllByOrderByTypeCodeAsc();
}
