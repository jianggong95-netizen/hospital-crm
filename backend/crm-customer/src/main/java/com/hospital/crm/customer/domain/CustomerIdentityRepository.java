package com.hospital.crm.customer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityRepository extends JpaRepository<CustomerIdentity, UUID> {

    Optional<CustomerIdentity> findFirstBySourceSystemAndIdentityTypeAndIdentityValueHash(
            String sourceSystem, String identityType, String identityValueHash);

    List<CustomerIdentity> findByCustomerId(UUID customerId);
}
