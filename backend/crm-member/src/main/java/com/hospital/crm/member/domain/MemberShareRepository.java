package com.hospital.crm.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberShareRepository extends JpaRepository<MemberShare, UUID> {

    List<MemberShare> findByOwnerCustomerIdOrderByCreatedAtDesc(UUID ownerCustomerId);

    List<MemberShare> findByBeneficiaryCustomerIdAndStatusOrderByCreatedAtDesc(UUID beneficiaryCustomerId, String status);

    List<MemberShare> findByBeneficiaryCustomerIdAndResourceTypeAndStatusAndCanConsumeTrueOrderByCreatedAtAsc(
            UUID beneficiaryCustomerId, String resourceType, String status);

    boolean existsByOwnerCustomerIdAndBeneficiaryCustomerIdAndResourceTypeAndStatus(
            UUID ownerCustomerId, UUID beneficiaryCustomerId, String resourceType, String status);
}
