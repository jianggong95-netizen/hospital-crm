package com.hospital.crm.homevisit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeServiceRepository extends JpaRepository<HomeService, UUID>,
        JpaSpecificationExecutor<HomeService> {

    Optional<HomeService> findByServiceCode(String serviceCode);

    List<HomeService> findByServiceCodeIn(Collection<String> serviceCodes);

    boolean existsByServiceCode(String serviceCode);
}
