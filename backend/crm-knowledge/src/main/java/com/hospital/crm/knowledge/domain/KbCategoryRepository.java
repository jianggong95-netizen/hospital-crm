package com.hospital.crm.knowledge.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KbCategoryRepository extends JpaRepository<KbCategory, UUID> {

    Optional<KbCategory> findByCode(String code);

    boolean existsByCode(String code);

    List<KbCategory> findAllByOrderBySortOrderAscCreatedAtAsc();
}
