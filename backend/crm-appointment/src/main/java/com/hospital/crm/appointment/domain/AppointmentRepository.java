package com.hospital.crm.appointment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    boolean existsByCustomerIdAndSlotIdAndStatusIn(UUID customerId, UUID slotId, Collection<String> statuses);

    List<Appointment> findBySlotIdAndStatusIn(UUID slotId, Collection<String> statuses);

    long countBySlotIdAndStatusIn(UUID slotId, Collection<String> statuses);

    @Query(value = "select nextval('crm_appointment_no_seq')", nativeQuery = true)
    Long nextAppointmentNo();
}
