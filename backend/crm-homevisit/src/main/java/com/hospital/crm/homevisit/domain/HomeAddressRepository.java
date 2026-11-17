package com.hospital.crm.homevisit.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HomeAddressRepository extends JpaRepository<HomeAddress, UUID> {

    List<HomeAddress> findByCustomerIdOrderByIsDefaultDescCreatedAtDesc(UUID customerId);

    List<HomeAddress> findByCustomerIdAndIsDefaultTrueAndStatus(UUID customerId, String status);
}
