package com.hospital.crm.callcenter.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageOutboxRepository
        extends JpaRepository<MessageOutbox, UUID>, JpaSpecificationExecutor<MessageOutbox> {

    /** 业务键幂等去重：同一 biz_key 只入队一次。 */
    Optional<MessageOutbox> findByBizKey(String bizKey);

    @Query(value = "select nextval('crm_msg_no_seq')", nativeQuery = true)
    long nextMsgNo();

    List<MessageOutbox> findByStatusAndNextRetryAtLessThanEqual(
            String status, Instant threshold, Pageable pageable);

    /**
     * 到期扫描：待发送或失败且到达重试时间的消息，按到期时间升序批量取出。
     */
    @Query("select m from MessageOutbox m where m.status in ('PENDING', 'FAILED') "
            + "and m.nextRetryAt is not null and m.nextRetryAt <= :now order by m.nextRetryAt asc")
    List<MessageOutbox> findDue(@Param("now") Instant now, Pageable pageable);

    /**
     * 条件抢占：仅待发送/失败且未超出重试上限的消息可进入发送中，返回 0 表示已被其他实例抢占。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageOutbox m set m.status = 'SENDING', m.updatedAt = :now "
            + "where m.id = :id and m.status in ('PENDING', 'FAILED') and m.retryCount < m.maxRetries")
    int markSending(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * 人工重试抢占：不受 max_retries 限制，仅要求当前处于待发送/失败状态。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageOutbox m set m.status = 'SENDING', m.updatedAt = :now "
            + "where m.id = :id and m.status in ('PENDING', 'FAILED')")
    int markSendingManual(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageOutbox m set m.status = 'SENT', m.sentAt = :now, "
            + "m.lastError = null, m.nextRetryAt = null, m.updatedAt = :now "
            + "where m.id = :id and m.status = 'SENDING'")
    int markSent(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * 发送失败：retry_count 数据库端自增，next_retry_at 由服务层按退避策略计算传入，
     * 超出上限时传 null 表示停止自动重试。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageOutbox m set m.status = 'FAILED', m.retryCount = m.retryCount + 1, "
            + "m.lastError = :lastError, m.nextRetryAt = :nextRetryAt, m.updatedAt = :now "
            + "where m.id = :id and m.status = 'SENDING'")
    int markFailed(@Param("id") UUID id, @Param("now") Instant now,
                   @Param("lastError") String lastError, @Param("nextRetryAt") Instant nextRetryAt);
}
