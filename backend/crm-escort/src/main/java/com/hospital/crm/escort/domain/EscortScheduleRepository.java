package com.hospital.crm.escort.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EscortScheduleRepository extends JpaRepository<EscortSchedule, UUID>,
        JpaSpecificationExecutor<EscortSchedule> {

    List<EscortSchedule> findByEscortIdAndWorkDate(UUID escortId, LocalDate workDate);
}
