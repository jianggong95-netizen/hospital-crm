package com.hospital.crm.homevisit.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HomeVisitExecutionRepository extends JpaRepository<HomeVisitExecution, UUID> {

    List<HomeVisitExecution> findByOrderIdOrderByVisitAtAsc(UUID orderId);
}
