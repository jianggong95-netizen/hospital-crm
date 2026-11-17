package com.hospital.crm.operation.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityParticipantRepository extends JpaRepository<ActivityParticipant, UUID> {

    Page<ActivityParticipant> findByActivityIdOrderByRegisteredAtDesc(UUID activityId, Pageable pageable);

    Optional<ActivityParticipant> findFirstByActivityIdAndPhoneHash(UUID activityId, String phoneHash);

    long countByActivityIdAndStatusNot(UUID activityId, String status);

    @Query("select p.activityId, count(p) from ActivityParticipant p "
            + "where p.activityId in :activityIds and p.status <> 'CANCELLED' group by p.activityId")
    List<Object[]> countActiveByActivityIds(@Param("activityIds") Collection<UUID> activityIds);
}
