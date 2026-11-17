package com.hospital.crm.operation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID>, JpaSpecificationExecutor<Invitation> {

    Optional<Invitation> findByInvitationNo(String invitationNo);

    List<Invitation> findByIdIn(List<UUID> ids);

    @Query(value = "select nextval('crm_invitation_no_seq')", nativeQuery = true)
    Long nextInvitationNo();
}
