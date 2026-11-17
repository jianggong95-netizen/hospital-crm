package com.hospital.crm.escort.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EscortRepository extends JpaRepository<Escort, UUID>, JpaSpecificationExecutor<Escort> {

    Optional<Escort> findByEscortNo(String escortNo);

    @Query(value = "select nextval('crm_escort_no_seq')", nativeQuery = true)
    Long nextEscortNo();
}
