package com.hospital.crm.family.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    List<FamilyMember> findByFamilyIdAndStatusOrderByAdminDescValidFromAsc(UUID familyId, String status);

    List<FamilyMember> findByFamilyIdOrderByValidFromDesc(UUID familyId);

    List<FamilyMember> findByFamilyIdInAndStatus(Collection<UUID> familyIds, String status);

    List<FamilyMember> findByFamilyIdAndAdminTrueAndStatus(UUID familyId, String status);

    Optional<FamilyMember> findByIdAndFamilyId(UUID id, UUID familyId);

    Optional<FamilyMember> findFirstByCustomerIdAndStatus(UUID customerId, String status);

    boolean existsByFamilyIdAndCustomerIdAndStatus(UUID familyId, UUID customerId, String status);
}
