package com.hospital.crm.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowupCategoryRepository extends JpaRepository<FollowupCategory, UUID> {

    Optional<FollowupCategory> findByCategoryCode(String categoryCode);

    List<FollowupCategory> findAllByOrderBySortNoAsc();

    List<FollowupCategory> findByStatusOrderBySortNoAsc(String status);
}
