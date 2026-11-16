package com.hospital.crm.customer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByOrderBySortOrderAscTagNameAsc();

    List<Tag> findAllByStatusOrderBySortOrderAscTagNameAsc(String status);

    Optional<Tag> findByTagCode(String tagCode);

    boolean existsByTagCode(String tagCode);

    boolean existsByTagName(String tagName);
}
