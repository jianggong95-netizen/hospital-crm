package com.hospital.crm.family.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface FamilyRepository extends JpaRepository<Family, UUID>, JpaSpecificationExecutor<Family> {

    @Query(value = "select nextval('crm_family_no_seq')", nativeQuery = true)
    Long nextFamilyNo();
}
