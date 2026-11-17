package com.hospital.crm.operation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findByLeadNo(String leadNo);

    List<Lead> findByPhoneHashAndStatusIn(String phoneHash, Collection<String> statuses);

    List<Lead> findByIdNoHashAndStatusIn(String idNoHash, Collection<String> statuses);

    List<Lead> findByIdIn(Collection<UUID> ids);

    long countByMergedIntoLeadId(UUID leadId);

    @Query(value = "select nextval('crm_lead_no_seq')", nativeQuery = true)
    Long nextLeadNo();
}
