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

public interface MemberCardRepository extends JpaRepository<MemberCard, UUID> {

    @Query(value = "select nextval('crm_member_card_no_seq')", nativeQuery = true)
    long nextCardNo();

    Page<MemberCard> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    @Query("select c from MemberCard c where (:customerId is null or c.customerId = :customerId) "
            + "and (:status is null or c.status = :status) order by c.createdAt desc")
    Page<MemberCard> search(@Param("customerId") UUID customerId,
                            @Param("status") String status,
                            Pageable pageable);

    List<MemberCard> findByCustomerIdAndStatusOrderByValidToAsc(UUID customerId, String status);

    List<MemberCard> findBySourceOrderId(UUID sourceOrderId);

    /**
     * 条件扣次：剩余次数不足时返回 0，保证并发扣次不超扣。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberCard c set c.usedTimes = c.usedTimes + :times, "
            + "c.remainingTimes = c.remainingTimes - :times, c.updatedAt = :now "
            + "where c.id = :id and c.status = 'ACTIVE' and c.remainingTimes >= :times")
    int consumeTimes(@Param("id") UUID id, @Param("times") int times, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberCard c set c.usedTimes = c.usedTimes - :times, "
            + "c.remainingTimes = c.remainingTimes + :times, c.status = 'ACTIVE', c.updatedAt = :now "
            + "where c.id = :id and c.usedTimes >= :times")
    int refundTimes(@Param("id") UUID id, @Param("times") int times, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberCard c set c.status = 'EXHAUSTED', c.updatedAt = :now "
            + "where c.id = :id and c.remainingTimes = 0 and c.status = 'ACTIVE'")
    int markExhausted(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberCard c set c.status = 'VOID', c.updatedAt = :now "
            + "where c.id = :id and c.status <> 'VOID'")
    int markVoid(@Param("id") UUID id, @Param("now") Instant now);
}
