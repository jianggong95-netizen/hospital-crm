package com.hospital.crm.homevisit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface HomeStaffQualificationRepository extends JpaRepository<HomeStaffQualification, UUID>,
        JpaSpecificationExecutor<HomeStaffQualification> {

    List<HomeStaffQualification> findByUserIdAndQualTypeAndStatus(UUID userId, String qualType, String status);

    boolean existsByUserIdAndQualTypeAndCertNo(UUID userId, String qualType, String certNo);
}
