package com.hospital.crm.member.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberLedgerRepository extends JpaRepository<MemberLedger, UUID> {

    Page<MemberLedger> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    List<MemberLedger> findByRelatedOrderIdOrderByCreatedAtAsc(UUID relatedOrderId);

    List<MemberLedger> findByAccountIdAndLedgerTypeOrderByCreatedAtDesc(UUID accountId, String ledgerType);
}
