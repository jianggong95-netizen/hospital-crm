package com.hospital.crm.member.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MemberCardUsageRepository extends JpaRepository<MemberCardUsage, UUID> {

    Page<MemberCardUsage> findByCardIdOrderByCreatedAtDesc(UUID cardId, Pageable pageable);

    List<MemberCardUsage> findByCardIdAndStatusOrderByCreatedAtDesc(UUID cardId, String status);

    /**
     * 返还扣次记录：仅 CONSUMED 可置为 REFUNDED，条件更新避免重复返还。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberCardUsage u set u.status = 'REFUNDED', u.refundedAt = :refundedAt "
            + "where u.id = :id and u.status = 'CONSUMED'")
    int markRefunded(@Param("id") UUID id, @Param("refundedAt") Instant refundedAt);
}
