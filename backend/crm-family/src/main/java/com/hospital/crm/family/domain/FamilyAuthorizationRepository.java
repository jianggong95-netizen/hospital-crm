package com.hospital.crm.family.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyAuthorizationRepository extends JpaRepository<FamilyAuthorization, UUID> {

    List<FamilyAuthorization> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    List<FamilyAuthorization> findByMemberIdAndStatus(UUID memberId, String status);

    Optional<FamilyAuthorization> findByIdAndFamilyIdAndMemberId(UUID id, UUID familyId, UUID memberId);

    Optional<FamilyAuthorization> findFirstByMemberIdAndGranteeCustomerIdAndScopeCodeOrderByCreatedAtDesc(
            UUID memberId, UUID granteeCustomerId, String scopeCode);

    boolean existsByMemberIdAndGranteeCustomerIdAndScopeCodeAndStatus(
            UUID memberId, UUID granteeCustomerId, String scopeCode, String status);
}
