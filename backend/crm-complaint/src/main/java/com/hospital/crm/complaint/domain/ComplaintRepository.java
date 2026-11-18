package com.hospital.crm.complaint.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID>,
        JpaSpecificationExecutor<Complaint> {

    @Query(value = "select nextval('crm_complaint_ticket_no_seq')", nativeQuery = true)
    long nextTicketNo();

    Optional<Complaint> findByTicketNo(String ticketNo);

    @Query("select c.id from Complaint c where c.dueAt is not null and c.dueAt < :now "
            + "and c.status not in ('CLOSED','CANCELLED') and c.escalated = false")
    List<UUID> findOverdueIds(@Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Complaint c set c.escalated = true, c.escalationLevel = c.escalationLevel + 1, "
            + "c.escalatedAt = :now, c.escalationReason = :reason, c.updatedAt = :now "
            + "where c.id = :id and c.escalated = false")
    int markEscalated(@Param("id") UUID id, @Param("now") Instant now, @Param("reason") String reason);
}
