package com.hospital.crm.member.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MemberAccountRepository extends JpaRepository<MemberAccount, UUID> {

    Optional<MemberAccount> findByCustomerId(UUID customerId);

    Optional<MemberAccount> findByAccountNo(String accountNo);

    @Query("select a from MemberAccount a where (:keyword is null or a.accountNo like %:keyword%) "
            + "and (:level is null or a.memberLevel = :level) "
            + "and (:status is null or a.status = :status)")
    Page<MemberAccount> search(@Param("keyword") String keyword,
                               @Param("level") String level,
                               @Param("status") String status,
                               Pageable pageable);

    @Query(value = "select nextval('crm_member_account_no_seq')", nativeQuery = true)
    long nextAccountNo();

    /**
     * 条件扣减余额：余额不足时返回 0，调用方据此返回业务错误，保证并发不超扣。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberAccount a set a.balance = a.balance - :amount, a.updatedAt = :now "
            + "where a.id = :id and a.status = 'active' and a.balance >= :amount")
    int deductBalance(@Param("id") UUID id, @Param("amount") BigDecimal amount, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberAccount a set a.balance = a.balance + :amount, a.updatedAt = :now "
            + "where a.id = :id and a.status = 'active'")
    int addBalance(@Param("id") UUID id, @Param("amount") BigDecimal amount, @Param("now") Instant now);

    /**
     * 条件扣减积分：积分不足时返回 0。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberAccount a set a.points = a.points - :points, a.updatedAt = :now "
            + "where a.id = :id and a.status = 'active' and a.points >= :points")
    int deductPoints(@Param("id") UUID id, @Param("points") long points, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberAccount a set a.points = a.points + :points, a.updatedAt = :now "
            + "where a.id = :id and a.status = 'active'")
    int addPoints(@Param("id") UUID id, @Param("points") long points, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MemberAccount a set a.memberLevel = :level, a.updatedAt = :now where a.id = :id")
    int updateLevel(@Param("id") UUID id, @Param("level") String level, @Param("now") Instant now);
}
